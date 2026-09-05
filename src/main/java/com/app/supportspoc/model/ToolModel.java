package com.app.supportspoc.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ToolModel {

    public record Entity(
            String key,
            String value,
            String source // "explicit" | "context" | "ambiguous"
    ) {}

    public record ClassificationItem(
            String intent,
            @JsonProperty("sub_intent") String subIntent,
            List<Entity> entities
    ) {}

//    public record TaskDecompositionOutput(
//            List<ClassificationItem> classifications
//    ) {}

    public record StateTransition(
            int stateIndex,
            long timestamp,
            List<String> intents,
            Map<String, String> activeEntity,
            Map<String, Object> payload
    ) {}

    public static class LookupConfig {
        private final String orderRef;
        private boolean needsReturnCheck;
        private final List<String> intents;

        public LookupConfig(String orderRef, boolean needsReturnCheck, List<String> intents) {
            this.orderRef = orderRef;
            this.needsReturnCheck = needsReturnCheck;
            this.intents = intents;
        }

        public String getOrderRef() { return orderRef; }
        public boolean isNeedsReturnCheck() { return needsReturnCheck; }
        public void setNeedsReturnCheck(boolean needsReturnCheck) { this.needsReturnCheck = needsReturnCheck; }
        public List<String> getIntents() { return intents; }
    }


    public enum EntitySource {
        @JsonProperty("explicit") EXPLICIT,
        @JsonProperty("context") CONTEXT,
        @JsonProperty("ambiguous") AMBIGUOUS
    }

    public record ExtractedEntity(
            @JsonPropertyDescription("e.g. 'order_reference', 'product_reference', 'status_filter'")
            String key,

            @JsonPropertyDescription("Extracted identifier value")
            String value,

            @JsonPropertyDescription("Source of entity classification")
            EntitySource source,
            String description
    ) {}

    public record IntentClassification(
            @JsonPropertyDescription("Matched workflow intent e.g., 'ORDER_TRACKING', 'ORDER_STATUS', 'ORDER_DETAILS', 'RETURN_ELIGIBILITY'")
            List<String> intents,

            @JsonProperty("sub_intent")
            List<String> subIntents,

            List<ExtractedEntity> entities,
            String text
    ) {
    }

    public record TaskDecompositionOutput(
            @JsonPropertyDescription("Ordered list of classifications for each decomposed sub-task.")
            List<IntentClassification> classifications
    ) {
        public TaskDecompositionOutput {
            if (classifications == null) {
                classifications = Collections.emptyList();
            }
        }
    }
}