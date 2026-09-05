package com.app.supportspoc.services;

import com.app.supportspoc.dto.SessionState;
import com.app.supportspoc.dto.ToolDefinition;
import com.app.supportspoc.model.*;
import com.app.supportspoc.pipeline.ToolCallTrace;
import com.app.supportspoc.pipeline.WorkflowExecutionContext;
import com.app.supportspoc.pipeline.WorkflowEngine;
import com.app.supportspoc.pipeline.WorkflowRegistry;
import com.app.supportspoc.pipeline.WorkflowResult;
import com.app.supportspoc.util.EntityDomainRegistry;
import com.app.supportspoc.util.LlmClient;
import com.app.supportspoc.util.Prompts;
import com.app.supportspoc.util.ToolIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;


import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Service
public class ToolRunnerServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(ToolRunnerServiceImpl.class);
    private static final Set<String> RETURN_INTENTS = Set.of("RETURN_ELIGIBILITY", "ORDER_RETURN", "REFUND_REQUEST");

    private final LlmClient llmClient;
    private final ShopifyOrderServiceImpl shopifyOrderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.classifier-model:gpt-4o-mini}")
    private String llmModel;

    @Value("${app.agent-model:gpt-4o}")
    private String agentModel;

    // Single-tenant/dev fallback used when a session hasn't gone through per-shop
    // OAuth (ShopifyTokenExchangeService isn't wired into Session yet). Real
    // multi-tenant use should rely on Session.getShopDomain()/getShopAccessToken().
    @Value("${app.shopify.shop-domain:}")
    private String defaultShopDomain;
    @Value("${app.shopify.access-token:}")
    private String defaultShopAccessToken;

    @Autowired
    ToolIndexService toolIndexService;
    @Autowired
    EntityDomainRegistry entityDomainRegistry;

    private final WorkflowRegistry workflowRegistry;
    private final WorkflowEngine workflowEngine;

    public ToolRunnerServiceImpl(LlmClient llmClient, ShopifyOrderServiceImpl shopifyOrderService, WorkflowRegistry workflowRegistry, WorkflowEngine workflowEngine) {
        this.llmClient = llmClient;
        this.shopifyOrderService = shopifyOrderService;
        this.workflowRegistry = workflowRegistry;
        this.workflowEngine = workflowEngine;
    }

    public CompletableFuture<ToolExecutionResult> runWithTools(Session session, ClassifierModels.ClassificationDecompositionOutput classification) {
        logger.info("Entering runWithTools: session={}, classification={}", session != null ? session.getSessionId() : null, classification);

        String originalMessage = classification.lines().stream().map(ClassifierModels.ClassificationDecompositionOutput.LineItem::text).collect(Collectors.joining(" "));

        return detailedIntentClassification(classification).thenCompose(decomposition -> {
            if (decomposition == null || decomposition.classifications() == null || decomposition.classifications().isEmpty()) {
                return CompletableFuture.completedFuture(new ToolExecutionResult("I could not understand your request. How else can I assist you?", List.of()));
            }

            logger.info("Decomposition completed for session_id={} with {} classifications.", session.getSessionId(), decomposition.classifications());
            // Steps 2-6: one PipelineTask per classification; entity resolution and
            // workflow lookup happen inline (ambiguous entities never reach a workflow).
            List<PipelineTask> tasks = buildTasks(decomposition);

            WorkflowExecutionContext ctx = new WorkflowExecutionContext(buildAmbientInput(session));

            // Steps 7-17: run every still-open task's workflow. Sharing ctx across tasks
            // is what makes step 12's cross-task tool-call dedup work.
            List<CompletableFuture<Void>> taskFutures = new ArrayList<>();
            for (PipelineTask task : tasks) {
                if (task.isTerminal()) continue; // e.g. NEEDS_CUSTOMER_INPUT, or no workflow found
                Map<String, Object> taskInput = buildTaskInput(task);
                taskFutures.add(workflowEngine.execute(task.workflowName(), taskInput, ctx).thenAccept(result -> applyWorkflowResult(task, result)));
            }

            return CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).thenCompose(v -> {
                // Step 18: every task now holds a terminal status.
                logger.info("session_id={} | task outcomes: {}", session.getSessionId(), tasks);

                // Steps 19-20: verified context in, customer-facing response out.
                return generateFinalResponse(originalMessage, tasks).thenApply(responseText -> {
                    // Step 21: persist.
                    persist(session, tasks);
                    List<String> toolCallSummaries = ctx.trace().stream().map(ToolCallTrace::toString).collect(Collectors.toList());
                    return new ToolExecutionResult(responseText, toolCallSummaries);
                });
            });
        });
    }

    /**
     * Steps 3-6: builds a task per classification, blocking ambiguous ones (step 4) and resolving intent -> workflow (step 6).
     */
    private List<PipelineTask> buildTasks(ToolModel.TaskDecompositionOutput decomposition) {
        logger.info("Entering buildTasks: decomposition={}", decomposition);
        List<PipelineTask> tasks = new ArrayList<>();
        int index = 0;
        for (ToolModel.IntentClassification classification : decomposition.classifications()) {
            PipelineTask task = new PipelineTask("t" + (++index), classification);
            List<ToolModel.ExtractedEntity> entities = classification.entities() != null ? classification.entities() : List.of();

            List<String> ambiguousKeys = entities.stream().filter(e -> e.source() == ToolModel.EntitySource.AMBIGUOUS).map(ToolModel.ExtractedEntity::key).collect(Collectors.toList());

            if (!ambiguousKeys.isEmpty()) {
                // Step 4: do not guess -- ask the customer instead.
                task.block(TaskStatus.NEEDS_CUSTOMER_INPUT, "could not uniquely resolve: " + String.join(", ", ambiguousKeys));
            } else if (classification.intents() == null || classification.intents().isEmpty()) {
                task.block(TaskStatus.FAILED, "no matching intent found for this request");
            } else {
                String matchedWorkflow = classification.intents().stream()
                        .filter(workflowRegistry::contains)
                        .findFirst()
                        .orElse(null);

                if (matchedWorkflow == null) {
                    task.block(TaskStatus.FAILED, "no workflow registered for intents: " + classification.intents());
                } else {
                    task.setWorkflowName(matchedWorkflow);
                }
            }
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * Step 5: the entities extracted for one task become that workflow's initial input.
     */
    private Map<String, Object> buildTaskInput(PipelineTask task) {
        logger.info("Entering buildTaskInput: task={}", task != null ? task.id() : null);
        Map<String, Object> input = new LinkedHashMap<>();
        for (ToolModel.ExtractedEntity entity : task.entities()) {
            if (entity.value() != null) {
                input.put(entity.key(), entity.value());
            }
        }
        return input;
    }

    /**
     * Session-level defaults available to every task's workflow (customer id, shop credentials, ...).
     */
    private Map<String, Object> buildAmbientInput(Session session) {
        logger.info("Entering buildAmbientInput: session={}", session != null ? session.getSessionId() : null);
        Map<String, Object> ambient = new LinkedHashMap<>();
        if (session.getCustomerEmail() != null) {
            // NOTE: no CUSTOMER_RESOLUTION lookup is wired here yet, so email is the
            // best identifier Session currently carries -- swap for a real customer_id
            // once that workflow is invoked up front.
            ambient.put("customer_id", session.getCustomerEmail());
            ambient.put("email", session.getCustomerEmail());
        }
        if (session.getCartId() != null) {
            ambient.put("cart_id", session.getCartId());
        }
        String shopDomain = session.getShopDomain() != null ? session.getShopDomain() : defaultShopDomain;
        String accessToken = session.getShopAccessToken() != null ? session.getShopAccessToken() : defaultShopAccessToken;
        if (shopDomain != null && !shopDomain.isBlank()) ambient.put("shop_domain", shopDomain);
        if (accessToken != null && !accessToken.isBlank()) ambient.put("access_token", accessToken);
        return ambient;
    }

    /**
     * Step 18: a successful workflow completes its task; a failed one blocks it, with the real reason attached.
     */
    private void applyWorkflowResult(PipelineTask task, WorkflowResult result) {
        logger.info("Entering applyWorkflowResult: task={}, result={}", task != null ? task.id() : null, result);
        if (result.success()) {
            task.results().putAll(result.output());
            task.setStatus(TaskStatus.COMPLETED);
        } else {
            task.block(TaskStatus.FAILED, result.error());
        }
    }

    /**
     * Step 20: the LLM only ever sees this -- original message plus verified task outcomes, never raw tool payloads or credentials.
     */
    private CompletableFuture<String> generateFinalResponse(String originalMessage, List<PipelineTask> tasks) {
        logger.info("Entering generateFinalResponse: originalMessage={}, tasksCount={}", originalMessage, tasks != null ? tasks.size() : 0);
        String verifiedContextJson = buildVerifiedContextJson(tasks);
        String userPrompt = """
                <customer_message>
                %s
                </customer_message>
                <verified_execution_context>
                %s
                </verified_execution_context>
                Respond to the customer now, following the rules above.
                """.formatted(originalMessage, verifiedContextJson);

        return llmClient.chatCompletion(agentModel, Prompts.FINAL_RESPONSE_SYSTEM_PROMPT, userPrompt).thenApply(reply -> reply != null && !reply.isBlank() ? reply : "I've noted your request, but I'm having trouble putting a response together right now. Could you try again?");
    }

    /**
     * Step 19: build verified execution context -- task outcomes only, nothing an LLM could mistake for permission to invent data.
     */
    private String buildVerifiedContextJson(List<PipelineTask> tasks) {
        logger.info("Entering buildVerifiedContextJson: tasksCount={}", tasks != null ? tasks.size() : 0);
        List<Map<String, Object>> taskSummaries = new ArrayList<>();
        for (PipelineTask task : tasks) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("task", task.text());
            summary.put("intent", task.intents());
            summary.put("status", task.status().name());
            if (task.blockReason() != null) summary.put("reason", task.blockReason());
            if (!task.results().isEmpty()) summary.put("result", task.results());
            taskSummaries.add(summary);
        }
        return finalTasksJson(taskSummaries);
    }

    /**
     * Step 21: persist entities, task/workflow status, and (best-effort) typed session state for the next turn.
     */
    private void persist(Session session, List<PipelineTask> tasks) {
        logger.info("Entering persist: session={}, tasksCount={}", session != null ? session.getSessionId() : null, tasks != null ? tasks.size() : 0);
        for (int i = 0; i < tasks.size(); i++) {
            PipelineTask task = tasks.get(i);
            session.addHistory(Map.of("task", task.text() != null ? task.text() : "", "intent", task.intents() != null ? task.intents() : "", "status", task.status().name()));
            session.addStateTransition(new ToolModel.StateTransition(i, System.currentTimeMillis(), task.intents() != null ? task.intents() : List.of(), Map.of(), Map.copyOf(task.results())));
        }
        updateTypedSessionState(session, tasks);
    }

    /**
     * Best-effort projection into the typed SessionState buckets (orders/products) used
     * for tiny, LLM-friendly context in direct/RAG prompts. Today's workflow "output"
     * maps (see resources/workflows/*.json) mostly surface individual fields rather than
     * a whole "order" object, so this is a no-op for most current workflows -- it's here
     * so workflows that DO output a full order object get reflected into session state
     * without further plumbing.
     */
    private void updateTypedSessionState(Session session, List<PipelineTask> tasks) {
        logger.info("Entering updateTypedSessionState: session={}, tasksCount={}", session != null ? session.getSessionId() : null, tasks != null ? tasks.size() : 0);
        for (PipelineTask task : tasks) {
            Object orderRaw = task.results().get("order");
            if (!(orderRaw instanceof Map<?, ?> orderMap)) continue;
            try {
                Object nameValue = orderMap.get("name") != null ? orderMap.get("name") : orderMap.get("id");
                if (nameValue == null) continue;
                String name = String.valueOf(nameValue);
                String status = orderMap.get("displayFulfillmentStatus") != null ? String.valueOf(orderMap.get("displayFulfillmentStatus")) : "";
                session.getSessionState().orders.put(name, new SessionState.OrderContext(name, status, List.of()));
                session.getSessionState().activeTopic = "ORDER";
                session.getSessionState().activeId = name;
            } catch (Exception e) {
                logger.debug("Skipped session_state order projection for task {}: {}", task.id(), e.getMessage());
            }
        }
    }

    private CompletableFuture<ToolModel.TaskDecompositionOutput> detailedIntentClassification(ClassifierModels.ClassificationDecompositionOutput classification) {
        logger.info("Entering detailedIntentClassification: classification={}", classification);
        return llmClient.callLLM(llmModel, "Decompose and classify customer support requests accurately against the taxonomy.", buildUserPromptForTools(classification.lines().stream().map(ClassifierModels.ClassificationDecompositionOutput.LineItem::text).collect(Collectors.joining("\n")), getTaxonomyForEachLine(classification)), ToolModel.TaskDecompositionOutput.class);
    }

    private String getTaxonomyForEachLine(ClassifierModels.ClassificationDecompositionOutput classification) {
        logger.info("Entering getTaxonomyForEachLine: classification={}", classification);
        try {
            List<ClassifierModels.ClassificationDecompositionOutput.SubIntentLineItem> enrichedLines = classification.lines().stream().map(line -> {
                Set<String> subIntents = new LinkedHashSet<>();
                String intent = line.getIntent();
                if (!intent.isBlank()) {
                    // 1. Get the list of ToolDefinitions for this category/intent key
                    // Assuming getFileToolIndex() returns Map<String, List<ToolDefinition>>:
                    List<ToolDefinition> tools = toolIndexService.getFileToolIndex().get(intent);

                    if (tools != null) {
                        // 2. Map ToolDefinition objects to their tool names (Strings)
                        for (ToolDefinition tool : tools) {
                            if (tool != null && tool.name() != null) {
                                subIntents.add(tool.name());
                            }
                        }
                    }
                }
                return new ClassifierModels.ClassificationDecompositionOutput.SubIntentLineItem(line.lineNumber(), line.text(), new ArrayList<>(subIntents), line.route());
            }).collect(Collectors.toList());
            // 3. Wrap them into a clean JSON-friendly structure or map
            Map<String, Object> promptPayload = new LinkedHashMap<>();
            promptPayload.put("is_compound", classification.isCompound());
            promptPayload.put("overall_route", classification.overallRoute());
            promptPayload.put("reasoning", classification.reasoning());
            promptPayload.put("lines", enrichedLines);

            // 4. Serialize to a JSON String
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonPromptString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(promptPayload);

            System.out.println(jsonPromptString);
            return jsonPromptString;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String finalTasksJson(Object tasks) {
        logger.info("Entering finalTasksJson: tasks={}", tasks);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
        } catch (Exception e) {
            return String.valueOf(tasks);
        }
    }

    private String buildUserPromptForTools(String history, String taxonomy) {
        logger.info("Entering buildUserPromptForTools: history={}, taxonomyLength={}", history, taxonomy != null ? taxonomy.length() : 0);
        return """
                <conversation_context>
                                %s
                </conversation_context>
                <taxonomy>
                %s
                </taxonomy>
                ### Instructions:
                1. CLASSIFY: For each task, select an `intent` or list of intents strictly matching the provided taxonomy.
                   - If no entry matches, set both to null. Do not invent categories.
                2. EXTRACT ENTITIES: Extract relevant parameters for each task.
                   - Distinguish CUSTOMER REFERENCES from SYSTEM IDs:
                     * "the boots" -> key: "product_reference", value: "boots", source: "explicit"
                     * "order 1042" -> key: "order_id", value: "1042", source: "explicit"
                   - Do NOT invent or infer unmentioned IDs (e.g., product_id, sku, tracking_id).
                   - Use source="context" only if unambiguously resolved from conversation history.
                   - Use source="ambiguous" if referenced but cannot be uniquely resolved.
                3. DO NOT EXECUTE: Do not check policies, calculate refunds, call tools, or answer the user.
                
                ### OUTPUT FORMAT:
                    Return ONLY valid JSON. Do not include markdown, explanations, reasoning, or any additional fields.
                    {
                      "classifications": [
                        {
                          "intents": "[list of matching intents from taxonomy, or [] if none]",
                          "entities": [
                            {
                              "key": "<entity_key>",
                              "value": "<entity_value>",
                              "source": "explicit | context | ambiguous".
                              "description": "<description of the entity in less than 10 words>"
                            },
                           "text":<text from taxonomy that corresponds to this classification, for reference>
                          ]
                        }
                      ]
                    }
                ### OUTPUT RULES:
                    - Return exactly one classification object for each task in <sub_tasks>, in the same order.
                    - Do not repeat the task text.
                    - Do not add fields not present in the output schema.
                    - Use an empty array `[]` when a task has no entities.
                    - Use `null` for unknown intent or sub_intent.
                    - Never infer an entity that is not explicitly stated or unambiguously resolved from context.
                    - `source="ambiguous"` means the customer reference exists but cannot be uniquely resolved.    
                Return ONLY valid JSON matching the schema.
                """.formatted(history, taxonomy);
    }

    public CompletableFuture<String> runDirect(Session session, String directSystemPrompt) {
        logger.info("Entering runDirect: session={}, directSystemPrompt={}", session != null ? session.getSessionId() : null, directSystemPrompt);
        String historyJson;
        try {
            historyJson = session.getHistory().isEmpty() ? "(none)" : objectMapper.writeValueAsString(session.getHistory());
        } catch (Exception e) {
            historyJson = "(none)";
        }

        String userPrompt = """
                <conversation_history>
                %s
                </conversation_history>
                
                Please respond directly and helpfully to the customer's latest request.
                """.formatted(historyJson);

        return llmClient.chatCompletion(agentModel, directSystemPrompt != null && !directSystemPrompt.isBlank() ? directSystemPrompt : "You are a helpful, professional customer support agent. Answer the customer's inquiry directly based on the conversation context.", userPrompt).thenApply(reply -> reply != null ? reply : "");
    }
}