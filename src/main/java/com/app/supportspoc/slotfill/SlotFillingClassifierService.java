package com.app.supportspoc.slotfill;

import com.app.supportspoc.slotfill.SlotFillingModels.EntitySpec;
import com.app.supportspoc.slotfill.SlotFillingModels.IntentClassificationResult;
import com.app.supportspoc.slotfill.SlotFillingModels.IntentSpec;
import com.app.supportspoc.util.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * LLM structured-output boundary: classifies one user message into one of the 5 catalog
 * intents and extracts whatever entity values it can confidently read off that message.
 * Knows nothing about slot-filling flow, state, or Shopify -- pure "text in, {intent,
 * entities} out".
 */
@Service
public class SlotFillingClassifierService {

    private static final Logger logger = LoggerFactory.getLogger(SlotFillingClassifierService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int HISTORY_TURNS_FOR_CONTEXT = 6;

    private final LlmClient llmClient;

    @Value("${app.slotfill.classifier-model:gpt-4o-mini}")
    private String classifierModel;

    public SlotFillingClassifierService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public CompletableFuture<IntentClassificationResult> classify(ConversationState state, String userMessage) {
        String systemPrompt = buildSystemPrompt(state);
        String userPrompt = buildUserPrompt(state, userMessage);

        return llmClient.callLLM(classifierModel, systemPrompt, userPrompt, IntentClassificationResult.class)
                .thenApply(result -> result != null ? result : new IntentClassificationResult("unknown", Map.of()))
                .exceptionally(ex -> {
                    logger.warn("slotfill_classification_failed - error={}", ex.getMessage());
                    return new IntentClassificationResult("unknown", Map.of());
                });
    }

    private String buildSystemPrompt(ConversationState state) {
        String taxonomy = renderTaxonomy();
        String activeIntentNote = state.currentIntent() == null
                ? "No intent is currently in progress."
                : "An intent is already in progress: \"" + state.currentIntent() + "\". Entities already collected: "
                        + toJson(state.collectedEntities()) + ". If the latest message looks like it's answering "
                        + "an outstanding question for THIS intent, keep returning this same intent. Only switch "
                        + "to a different intent if the customer clearly asked for something else.";

        return """
                You are the intent classifier for a Shopify order support agent that recognizes exactly 5 intents.

                <intent_taxonomy>
                %s
                </intent_taxonomy>

                <conversation_state>
                %s
                </conversation_state>

                Rules:
                - Pick the single best-matching intent name from the taxonomy above, or "unknown" only if NOTHING
                  in the message relates to any of the 5 intents.
                - Classify by what the customer is trying to DO, not by whether its required entities are already
                  present in this message. A required entity being missing is normal and expected -- a separate
                  slot-filling step asks for it afterward. Never return "unknown" just because a required field
                  (like an order ID) hasn't been mentioned yet.
                - If the message asks for several things and only one part matches an intent in the taxonomy
                  (e.g. it also asks about a return, exchange, or something else this taxonomy has no intent for),
                  still classify using the part that DOES match, and ignore the unsupported part entirely. Only
                  return "unknown" if no part of the message matches anything in the taxonomy.
                - Extract entity values ONLY for fields defined on the intent you picked, and ONLY when the value
                  is actually present in the conversation -- never invent or guess values.
                - Return values with sensible native JSON types (numbers as numbers, booleans as true/false,
                  addresses/line items as nested objects/arrays), not everything as strings.
                - Do not include entities that are not part of the chosen intent's schema.

                Examples:
                - "Where is my order 1234?" -> {"intent": "fetch_order", "entities": {"orderId": "1234"}}
                - "Where is my order?" (no id given yet) -> {"intent": "fetch_order", "entities": {}}
                - "Where is my order, and can I return the boots if they don't fit?" -> {"intent": "fetch_order",
                  "entities": {}} (the return question isn't in this taxonomy, so it's ignored; the order lookup
                  is still classified even without an ID)
                - "What's the weather like today?" -> {"intent": "unknown", "entities": {}}

                Return ONLY valid JSON: {"intent": "<intent_name_or_unknown>", "entities": { ... }}
                """.formatted(taxonomy, activeIntentNote);
    }

    private String buildUserPrompt(ConversationState state, String userMessage) {
        var history = state.history();
        String historyText = history.stream()
                .skip(Math.max(0, history.size() - HISTORY_TURNS_FOR_CONTEXT))
                .map(t -> t.role() + ": " + t.content())
                .collect(Collectors.joining("\n"));

        return """
                <recent_history>
                %s
                </recent_history>

                <latest_message>
                %s
                </latest_message>
                """.formatted(historyText.isBlank() ? "(none)" : historyText, userMessage);
    }

    private String renderTaxonomy() {
        StringBuilder sb = new StringBuilder();
        for (IntentSpec spec : IntentCatalog.all().values()) {
            sb.append("- ").append(spec.name()).append(": ").append(spec.description()).append('\n');
            for (EntitySpec e : spec.entities()) {
                sb.append("    * ").append(e.name()).append(" (").append(e.type()).append(", ")
                        .append(e.required() ? "required" : "optional").append("): ")
                        .append(e.description()).append('\n');
            }
        }
        return sb.toString();
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
