package com.app.supportspoc.services;

import com.app.supportspoc.dto.SessionState;
import com.app.supportspoc.dto.ToolDefinition;
import com.app.supportspoc.model.*;
import com.app.supportspoc.util.EntityDomainRegistry;
import com.app.supportspoc.util.LlmClient;
import com.app.supportspoc.util.ToolIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;


import org.springframework.beans.factory.annotation.Value;
import  com.fasterxml.jackson.databind.ObjectMapper;

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
    @Autowired
    ToolIndexService toolIndexService;
    @Autowired
    EntityDomainRegistry entityDomainRegistry;

    public ToolRunnerServiceImpl(LlmClient llmClient, ShopifyOrderServiceImpl shopifyOrderService) {
        this.llmClient = llmClient;
        this.shopifyOrderService = shopifyOrderService;
    }

    public CompletableFuture<ToolExecutionResult> runWithTools(Session session, ClassifierModels.ClassificationDecompositionOutput classification) {
        logger.info("Running tool execution for session_id={} with {} tasks.", session.getSessionId(), classification.lines());

        return detailedIntentClassification(classification).thenCompose(decomposition -> {
            if (decomposition == null || decomposition.classifications() == null || decomposition.classifications().isEmpty()) {
                return CompletableFuture.completedFuture(
                        new ToolExecutionResult("I could not understand your request. How else can I assist you?", List.of())
                );
            }
            logger.info("Decomposition completed for session_id={} with {} classifications.", session.getSessionId(), decomposition.classifications());
            for(ClassifierModels.ClassificationDecompositionOutput.LineItem line : classification.lines()) {
                logger.info("Line {}: text='{}', intents={}, route={}", line.lineNumber(), line.text(), line.intents(), line.route());
            }
            return null;
        });
    }

    private CompletableFuture<ToolModel.TaskDecompositionOutput> detailedIntentClassification(ClassifierModels.ClassificationDecompositionOutput classification) {
        return llmClient.callLLM(
                llmModel,
                "Decompose and classify customer support requests accurately against the taxonomy.",
                buildUserPromptForTools(classification.lines().stream().map(ClassifierModels.ClassificationDecompositionOutput.LineItem::text)
                        .collect(Collectors.joining("\n")),getTaxonomyForEachLine(classification) ),
                ToolModel.TaskDecompositionOutput.class
        );
    }

    private String getTaxonomyForEachLine(ClassifierModels.ClassificationDecompositionOutput classification) {
        try {
            List<ClassifierModels.ClassificationDecompositionOutput.LineItem> enrichedLines = classification.lines().stream()
                    .map(line -> {
                        if (line.intents() == null || line.intents().isEmpty()) {
                            return line;
                        }

                        Set<String> expandedIntents = new LinkedHashSet<>();
                        for (String intent : line.intents()) {
                            if (intent != null && !intent.isBlank()) {
                                expandedIntents.add(intent);

                                // 1. Get the list of ToolDefinitions for this category/intent key
                                // Assuming getFileToolIndex() returns Map<String, List<ToolDefinition>>:
                                List<ToolDefinition> tools = toolIndexService.getFileToolIndex().get(intent);

                                if (tools != null) {
                                    // 2. Map ToolDefinition objects to their tool names (Strings)
                                    for (ToolDefinition tool : tools) {
                                        if (tool != null && tool.name() != null) {
                                            expandedIntents.add(tool.name());
                                        }
                                    }
                                }
                            }
                        }

                        return new ClassifierModels.ClassificationDecompositionOutput.LineItem(
                                line.lineNumber(),
                                line.text(),
                                new ArrayList<>(expandedIntents),
                                line.route()
                        );
                    })
                    .collect(Collectors.toList());

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
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
        } catch (Exception e) {
            return String.valueOf(tasks);
        }
    }

    private String buildUserPromptForTools(String history, String taxonomy) {
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
                      "intent": ["<intent> or null if no match"],
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

        return llmClient.chatCompletion(
                agentModel,
                directSystemPrompt != null && !directSystemPrompt.isBlank()
                        ? directSystemPrompt
                        : "You are a helpful, professional customer support agent. Answer the customer's inquiry directly based on the conversation context.",
                userPrompt
        ).thenApply(reply -> reply != null ? reply : "");
    }
}