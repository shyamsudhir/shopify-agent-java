package com.app.supportspoc.services;


import com.app.supportspoc.model.ClassifierModels;
import com.app.supportspoc.model.IntentRegistryModels;
import com.app.supportspoc.model.Route;
import com.app.supportspoc.model.Session.Turn;
import com.app.supportspoc.util.LlmClient;
import com.app.supportspoc.util.ToolIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ClassifierServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(ClassifierServiceImpl.class);
    private final LlmClient llmClient;

    @Value("${app.classifier-model:gpt-4o-mini}")
    private String classifierModel;
    @Autowired
    ToolIndexService toolIndexService;;

    public ClassifierServiceImpl(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    // this consumes 400 input tokens
    public CompletableFuture<ClassifierModels.ClassificationDecompositionOutput> classify(
            String message,
            List<Turn> conversationHistory
    ) {
        // Take up to the last 6 turns
        int startIdx = Math.max(0, conversationHistory.size() - 6);
        List<Turn> recentTurns = conversationHistory.subList(startIdx, conversationHistory.size());

        String history = recentTurns.isEmpty()
                ? "(none)"
                : recentTurns.stream()
                .map(t -> (t.role() != null ? t.role() : "user") + ": " + (t.content() != null ? t.content() : ""))
                .collect(Collectors.joining("\n"));

        String userPrompt = buildUserPrompt(message, history, formatTaxonomy(toolIndexService.getHigherIntents()));

        String systemPrompt = "You are the intent router and query decomposition engine for a Shopify customer support AI. "
                + "Decompose incoming messages into atomic tasks, determine operational routes (DIRECT, RAG, TOOL), "
                + "and extract entities without inventing IDs."
                + "Return ONLY valid JSON.\n" +
                "Do not include markdown or explanatory text.";

        return llmClient.callLLM(classifierModel, systemPrompt, userPrompt, ClassifierModels.ClassificationDecompositionOutput.class)
                .thenApply(parsed -> {
                    if (parsed == null) {
                        logger.warn("Classifier returned null/unparsed output.");
                        return new ClassifierModels.ClassificationDecompositionOutput(
                                false,
                                Route.UNSUPPORTED.name(),
                                "no reasoning provided",
                                Collections.emptyList()
                        );
                    }
                    return parsed;
                });
    }

    private String buildUserPrompt(String message, String history, String taxonomy) {
        return """
        <customer_message>
        %s
        </customer_message>

        <intents_taxonomy>
        %s
        </intents_taxonomy>

        ### Route Definitions:
        - DIRECT: Conversational greetings ("hello", "thanks"), small talk, closings, general non-store guidance (no lookup or policy doc needed).
        - TOOL: Dynamic operational actions needing database/API execution (order lookup/tracking, checking live inventory, modifying cart, initiating returns, cancelling orders).
        - UNSUPPORTED: Spam, offensive language, or completely out-of-scope requests.

        ### Instructions:
        1. DECOMPOSE BY LINE:
           - Break down the `<customer_message>` line-by-line (ignore blank lines).
        2. MAP INTENTS & ROUTE:
           - For each line, map one or more relevant intents strictly from `<intents_taxonomy>`.
           - Assign a `route` (`DIRECT`, `TOOL`, or `UNSUPPORTED`) to each line.
        3. AGGREGATE ROUTING:
           - Set `is_compound: false` if there is only 1 line/action; set `is_compound: true` if there are multiple lines/actions.
           - Set `overall_route` to `MULTI_ACTION` if `is_compound` is true; otherwise, set it to that line's `route`.

        ### Expected Output Format:
        ```json
        {
          "is_compound": true,
          "overall_route": "MULTI_ACTION",
          "lines": [
            {
              "line_number": 1,
              "text": "Where is my order 1042?",
              "intent": <intent name from taxonomy>,
              "route": "TOOL"
            }
          ]
        }
        ```
        """.formatted(message, taxonomy);
    }

    private String formatTaxonomy(List<IntentRegistryModels.HigherIntentDefinition> higherIntents) {
        if (higherIntents == null || higherIntents.isEmpty()) {
            return "(none)";
        }
        return higherIntents.stream()
                .map(intent -> "- " + intent.name() + ": " + intent.description())
                .collect(Collectors.joining("\n"));
    }
}