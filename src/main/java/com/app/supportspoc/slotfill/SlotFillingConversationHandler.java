package com.app.supportspoc.slotfill;

import com.app.supportspoc.slotfill.SlotFillingModels.ConversationStatus;
import com.app.supportspoc.slotfill.SlotFillingModels.EntitySpec;
import com.app.supportspoc.slotfill.SlotFillingModels.IntentClassificationResult;
import com.app.supportspoc.slotfill.SlotFillingModels.IntentSpec;
import com.app.supportspoc.util.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrates one conversation turn end to end: classify -> merge into state -> either
 * ask for what's still missing, or execute the mock Shopify call -> reply. This is the
 * only class that knows the full flow; classification, state storage, and the Shopify
 * call are each delegated to their own single-purpose collaborator.
 */
@Service
public class SlotFillingConversationHandler {

    private static final Logger logger = LoggerFactory.getLogger(SlotFillingConversationHandler.class);

    private final ConversationStateManager stateManager;
    private final SlotFillingClassifierService classifierService;
    private final ShopifyMockService shopifyMockService;
    private final LlmClient llmClient;

    @Value("${app.slotfill.response-model:gpt-4o-mini}")
    private String responseModel;

    public SlotFillingConversationHandler(ConversationStateManager stateManager,
                                           SlotFillingClassifierService classifierService,
                                           ShopifyMockService shopifyMockService,
                                           LlmClient llmClient) {
        this.stateManager = stateManager;
        this.classifierService = classifierService;
        this.shopifyMockService = shopifyMockService;
        this.llmClient = llmClient;
    }

    public CompletableFuture<SlotFillingTurnResult> handleMessage(String sessionId, String userMessage) {
        ConversationState state = stateManager.getOrCreate(sessionId);
        state.appendTurn("user", userMessage);

        return classifierService.classify(state, userMessage)
                .thenCompose(classification -> applyClassification(state, classification));
    }

    private CompletableFuture<SlotFillingTurnResult> applyClassification(
            ConversationState state, IntentClassificationResult classification) {

        String classifiedIntent = classification.intent();
        boolean isKnownIntent = IntentCatalog.contains(classifiedIntent);

        if (!isKnownIntent && state.currentIntent() == null) {
            // Nothing in progress, and this message doesn't match any of the 5 intents.
            String reply = "I can help with looking up an order, listing recent orders, orders in a date range, "
                    + "creating a new order, or cancelling one. What would you like to do?";
            state.appendTurn("assistant", reply);
            return CompletableFuture.completedFuture(
                    new SlotFillingTurnResult(state.sessionId(), reply, null, null, Map.of(), null));
        }

        if (isKnownIntent && !classifiedIntent.equals(state.currentIntent())) {
            // First message for this intent, or the customer switched topics mid slot-filling.
            state.startIntent(classifiedIntent);
        }
        // else: classifier returned "unknown" while an intent is already in progress -- treat this
        // message as a (possibly terse) answer to the outstanding question for that intent.

        IntentSpec spec = IntentCatalog.get(state.currentIntent());
        mergeEntities(state, spec, classification.entities());

        List<EntitySpec> missing = missingRequiredEntities(state, spec);
        if (!missing.isEmpty()) {
            return askForMissing(state, spec, missing);
        }

        applyDefaults(state, spec);
        state.markReadyToExecute();
        return execute(state, spec);
    }

    /** Only accepts entity keys the active intent actually declares -- ignores anything else the LLM returned. */
    private void mergeEntities(ConversationState state, IntentSpec spec, Map<String, Object> extracted) {
        if (extracted == null) return;
        for (Map.Entry<String, Object> entry : extracted.entrySet()) {
            if (spec.entity(entry.getKey()).isPresent()) {
                state.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private List<EntitySpec> missingRequiredEntities(ConversationState state, IntentSpec spec) {
        return spec.requiredEntities().stream().filter(e -> !state.has(e.name())).collect(Collectors.toList());
    }

    /** Fills in constants/sane defaults for optional slots that were never explicitly provided. */
    private void applyDefaults(ConversationState state, IntentSpec spec) {
        for (EntitySpec e : spec.entities()) {
            if (!e.required() && !state.has(e.name()) && e.defaultValue() != null) {
                state.put(e.name(), e.defaultValue());
            }
        }
    }

    private CompletableFuture<SlotFillingTurnResult> askForMissing(
            ConversationState state, IntentSpec spec, List<EntitySpec> missing) {

        String missingDescriptions = missing.stream()
                .map(EntitySpec::description)
                .collect(Collectors.joining("; "));

        String systemPrompt = "You are a concise, friendly customer support agent for a Shopify store. Ask the "
                + "customer for ONLY the missing information listed below, in one short natural sentence. Do not "
                + "mention internal field names -- phrase it naturally. Do not ask about anything not listed.";
        String userPrompt = "Intent in progress: " + spec.description() + "\nStill missing: " + missingDescriptions;

        return llmClient.chatCompletion(responseModel, systemPrompt, userPrompt)
                .exceptionally(ex -> "")
                .thenApply(reply -> {
                    String finalReply = (reply == null || reply.isBlank())
                            ? templateAskForMissing(missing)
                            : reply.trim();
                    state.appendTurn("assistant", finalReply);
                    return new SlotFillingTurnResult(state.sessionId(), finalReply,
                            ConversationStatus.COLLECTING, spec.name(), state.collectedEntities(), null);
                });
    }

    private String templateAskForMissing(List<EntitySpec> missing) {
        return "Could you provide the following? "
                + missing.stream().map(EntitySpec::description).collect(Collectors.joining(" Also, "));
    }

    private CompletableFuture<SlotFillingTurnResult> execute(ConversationState state, IntentSpec spec) {
        Map<String, Object> entitiesForCall = state.collectedEntities();

        Map<String, Object> mockResult;
        try {
            mockResult = shopifyMockService.execute(spec.name(), entitiesForCall);
        } catch (Exception e) {
            logger.error("slotfill_execution_failed - intent={}, error={}", spec.name(), e.getMessage());
            String reply = "I collected everything I need, but hit an error trying to complete that. Please try again.";
            state.appendTurn("assistant", reply);
            return CompletableFuture.completedFuture(new SlotFillingTurnResult(
                    state.sessionId(), reply, ConversationStatus.COLLECTING, spec.name(), entitiesForCall, null));
        }

        return summarize(spec, entitiesForCall, mockResult).thenApply(reply -> {
            state.appendTurn("assistant", reply);
            state.completeExecution(mockResult);
            return new SlotFillingTurnResult(state.sessionId(), reply, ConversationStatus.EXECUTED,
                    spec.name(), entitiesForCall, mockResult);
        });
    }

    private CompletableFuture<String> summarize(IntentSpec spec, Map<String, Object> entities, Map<String, Object> result) {
        String systemPrompt = "You are a concise customer support agent. Summarize the result below for the "
                + "customer in one or two short, natural sentences. Do not show raw field names or JSON.";
        String userPrompt = "Intent: " + spec.description() + "\nInput: " + entities + "\nResult: " + result;

        return llmClient.chatCompletion(responseModel, systemPrompt, userPrompt)
                .exceptionally(ex -> "")
                .thenApply(reply -> (reply == null || reply.isBlank()) ? "Done: " + result : reply.trim());
    }

    /** One turn's outcome, returned to the controller. */
    public record SlotFillingTurnResult(
            String sessionId,
            String reply,
            ConversationStatus status,
            String intent,
            Map<String, Object> collectedEntities,
            Map<String, Object> result
    ) {}
}
