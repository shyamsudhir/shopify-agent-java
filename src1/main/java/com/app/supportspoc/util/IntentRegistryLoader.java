package com.app.supportspoc.util;


import com.app.supportspoc.model.IntentRegistryModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
public class IntentRegistryLoader {

    private static final Logger logger = LoggerFactory.getLogger(IntentRegistryLoader.class);

    private static final Set<String> EXCLUDED_FILES = Set.of(
            "tools.json",
            "unified_intents.json",
            "manifest.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private UnifiedManifest unifiedManifest = new UnifiedManifest(new LinkedHashMap<>());
    private List<HigherIntentDefinition> higherIntents = new ArrayList<>();
    private String unifiedJsonStr = "";

    public IntentRegistryLoader() {
    }

    /**
     * Scans the tools directory/classpath for domain JSON files and builds the in-memory registry.
     */
    public LoadIntentsResult loadAllIntents(String toolsLocation) {
        Map<String, DomainManifestEntry> intentsMapping = new LinkedHashMap<>();
        List<HigherIntentDefinition> loadedHigherIntents = new ArrayList<>();

        try {
            Resource[] resources = resolveJsonResources(toolsLocation);
            Arrays.sort(resources, Comparator.comparing(r -> Optional.ofNullable(r.getFilename()).orElse("")));

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || EXCLUDED_FILES.contains(filename)) {
                    continue;
                }

                try (InputStream is = resource.getInputStream()) {
                    JsonNode data = objectMapper.readTree(is);

                    String higherIntent = data.path("higher_intent").asText(null);
                    if (higherIntent == null || higherIntent.isBlank()) {
                        continue;
                    }

                    String description = data.path("description").asText("");
                    loadedHigherIntents.add(new HigherIntentDefinition(higherIntent, description, "function"));

                    int toolCount = data.path("tool_count").asInt(0);
                    JsonNode rawTools = data.get("tools");
                    JsonNode nestedIntentsRaw = data.get("nested_intents");

                    Map<String, Map<String, Object>> toolMetadataMap = buildToolMetadataMap(rawTools);
                    List<NestedIntentEntry> nestedIntentsList = new ArrayList<>();

                    // Case 1: Flattened tools array (with sub_intent inside each tool)
                    if ((nestedIntentsRaw == null || nestedIntentsRaw.isNull() || nestedIntentsRaw.isMissingNode())
                            && rawTools != null && rawTools.isArray()) {

                        Map<String, List<Map<String, Object>>> subIntentMap = new LinkedHashMap<>();

                        for (JsonNode toolNode : rawTools) {
                            Map<String, Object> expanded = expandToolEntry(toolNode, toolMetadataMap);
                            if (expanded == null) continue;

                            String subKey = toolNode.path("sub_intent").asText("general");
                            subIntentMap.computeIfAbsent(subKey, k -> new ArrayList<>()).add(expanded);
                        }

                        for (Map.Entry<String, List<Map<String, Object>>> entry : subIntentMap.entrySet()) {
                            nestedIntentsList.add(new NestedIntentEntry(
                                    entry.getKey(),
                                    "",
                                    entry.getValue().size(),
                                    entry.getValue()
                            ));
                        }

                        // Case 2: Array-based nested_intents
                    } else if (nestedIntentsRaw != null && nestedIntentsRaw.isArray()) {
                        for (JsonNode item : nestedIntentsRaw) {
                            String key = item.has("intent_key") ? item.path("intent_key").asText() : item.path("nested_intent").asText();
                            String itemDesc = item.path("description").asText("");

                            List<Map<String, Object>> tools = new ArrayList<>();
                            JsonNode itemTools = item.get("tools");
                            if (itemTools != null && itemTools.isArray()) {
                                for (JsonNode toolEntry : itemTools) {
                                    Map<String, Object> expanded = expandToolEntry(toolEntry, toolMetadataMap);
                                    if (expanded != null) {
                                        tools.add(expanded);
                                    }
                                }
                            }

                            nestedIntentsList.add(new NestedIntentEntry(key, itemDesc, tools.size(), tools));
                        }

                        // Case 3: Legacy Map-based nested_intents
                    } else if (nestedIntentsRaw != null && nestedIntentsRaw.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> fields = nestedIntentsRaw.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            String nestedKey = field.getKey();
                            JsonNode nestedVal = field.getValue();

                            String nestedDesc = nestedVal.path("description").asText("");
                            List<Map<String, Object>> tools = new ArrayList<>();
                            JsonNode valTools = nestedVal.get("tools");

                            if (valTools != null && valTools.isArray()) {
                                for (JsonNode toolEntry : valTools) {
                                    Map<String, Object> expanded = expandToolEntry(toolEntry, toolMetadataMap);
                                    if (expanded != null) {
                                        tools.add(expanded);
                                    }
                                }
                            }

                            nestedIntentsList.add(new NestedIntentEntry(nestedKey, nestedDesc, tools.size(), tools));
                        }
                    }

                    intentsMapping.put(higherIntent, new DomainManifestEntry(
                            filename,
                            description,
                            toolCount,
                            nestedIntentsList
                    ));

                } catch (Exception e) {
                    logger.warn("tool_file_parse_failed - file={}, error={}", filename, e.getMessage());
                }
            }

        } catch (IOException e) {
            logger.error("Failed to discover tools in path: {}", toolsLocation, e);
        }

        this.unifiedManifest = new UnifiedManifest(Collections.unmodifiableMap(intentsMapping));
        this.higherIntents = Collections.unmodifiableList(loadedHigherIntents);

        try {
            this.unifiedJsonStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this.unifiedManifest);
        } catch (Exception e) {
            this.unifiedJsonStr = "{}";
        }

        return new LoadIntentsResult(this.unifiedManifest, this.higherIntents);
    }

    /**
     * Loads all workflow JSON files from a directory into a Map.
     */
    public Map<String, Object> loadWorkflows(String workflowsLocation) {
        Map<String, Object> workflowsByFile = new LinkedHashMap<>();

        try {
            Resource[] resources = resolveJsonResources(workflowsLocation);
            Arrays.sort(resources, Comparator.comparing(r -> Optional.ofNullable(r.getFilename()).orElse("")));

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;

                try (InputStream is = resource.getInputStream()) {
                    Object parsed = objectMapper.readValue(is, Object.class);
                    workflowsByFile.put(filename, parsed);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to parse workflow file: " + filename, e);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load workflows from location: {}", workflowsLocation, e);
        }

        return Collections.unmodifiableMap(workflowsByFile);
    }

    // --- Helpers ---

    private Map<String, Map<String, Object>> buildToolMetadataMap(JsonNode rawTools) {
        Map<String, Map<String, Object>> metadataMap = new LinkedHashMap<>();
        if (rawTools == null || !rawTools.isArray()) {
            return metadataMap;
        }

        for (JsonNode tool : rawTools) {
            if (!tool.isObject()) continue;

            String name = tool.path("name").asText(null);
            if (name == null || name.isBlank()) continue;

            Map<String, Object> attributes = new LinkedHashMap<>();
            tool.fields().forEachRemaining(entry -> {
                if (!"name".equals(entry.getKey())) {
                    attributes.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
                }
            });

            metadataMap.put(name, attributes);
        }

        return metadataMap;
    }

    private Map<String, Object> expandToolEntry(
            JsonNode toolEntry,
            Map<String, Map<String, Object>> toolMetadataMap
    ) {
        if (toolEntry.isObject()) {
            String toolName = toolEntry.path("name").asText(null);
            if (toolName == null || toolName.isBlank()) return null;

            Map<String, Object> toolMetadata = new LinkedHashMap<>();
            Map<String, Object> finalToolMetadata = toolMetadata;
            toolEntry.fields().forEachRemaining(entry -> {
                if (!"name".equals(entry.getKey())) {
                    finalToolMetadata.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
                }
            });

            if (finalToolMetadata.isEmpty()) {
                toolMetadata = (Map<String, Object>) finalToolMetadata.getOrDefault(toolName, Collections.emptyMap());
            }

            return Map.of(toolName, toolMetadata);

        } else if (toolEntry.isTextual()) {
            String toolName = toolEntry.asText();
            Map<String, Object> meta = toolMetadataMap.getOrDefault(toolName, Collections.emptyMap());
            return Map.of(toolName, meta);
        }

        return null;
    }

    private Resource[] resolveJsonResources(String location) throws IOException {
        String cleanLocation = location.trim();
        if (!cleanLocation.startsWith("classpath:") && !cleanLocation.startsWith("file:")) {
            cleanLocation = "file:" + cleanLocation;
        }
        String pattern = cleanLocation.endsWith("/") ? cleanLocation + "*.json" : cleanLocation + "/*.json";
        return resolver.getResources(pattern);
    }

    public UnifiedManifest getUnifiedManifest() { return unifiedManifest; }
    public List<HigherIntentDefinition> getHigherIntents() { return higherIntents; }
    public String getUnifiedJsonString() { return unifiedJsonStr; }
}