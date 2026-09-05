package com.app.supportspoc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class IntentRegistryModels {

    public record HigherIntentDefinition(
            String name,
            String description,
            String type
    ) {
        public HigherIntentDefinition(String name, String description) {
            this(name, description, "function");
        }
    }

    public record NestedIntentEntry(
            @JsonProperty("nested_intent") String nestedIntent,
            String description,
            @JsonProperty("tool_count") int toolCount,
            List<Map<String, Object>> tools
    ) {}

    public record DomainManifestEntry(
            String file,
            String description,
            @JsonProperty("tool_count") int toolCount,
            @JsonProperty("nested_intents") List<NestedIntentEntry> nestedIntents
    ) {}

    public record UnifiedManifest(
            @JsonProperty("intents_mapping") Map<String, DomainManifestEntry> intentsMapping
    ) {}

    public record LoadIntentsResult(
            UnifiedManifest manifest,
            List<HigherIntentDefinition> higherIntents
    ) {}
}