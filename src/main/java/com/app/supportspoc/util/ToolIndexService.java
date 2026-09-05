package com.app.supportspoc.util;


import com.app.supportspoc.dto.ToolDefinition;
import com.app.supportspoc.model.IntentRegistryModels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class ToolIndexService {

    private static final Logger logger = LoggerFactory.getLogger(ToolIndexService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntentRegistryLoader registryLoader;

    @Value("${app.tools-dir:classpath:tools/}")
    private String toolsDirLocation;

    @Value("${app.workflow-dir:classpath:workflows/}")
    private String workflowDirLocation;

    @Getter
    private Map<String, List<ToolDefinition>> fileToolIndex = new HashMap<>();
    @Getter
    private Map<String, Object> unifiedIntentsCache = new HashMap<>();
    @Getter
    private List<IntentRegistryModels.HigherIntentDefinition> higherIntents = new ArrayList<>();
    /**
     * -- GETTER --
     * Raw per-file workflow JSON (filename -> parsed content), as loaded at startup.
     */
    @Getter
    private Map<String, Object> workflows = new HashMap<>();

    public ToolIndexService(IntentRegistryLoader registryLoader) {
        this.registryLoader = registryLoader;
    }

    @PostConstruct
    public void init() {
        this.fileToolIndex = buildFileToolIndex(toolsDirLocation);

        var intentsResult = registryLoader.loadAllIntents(toolsDirLocation);
//        this.unifiedIntentsCache = intentsResult.unifiedIntents();
        this.higherIntents = intentsResult.higherIntents();

        this.workflows = registryLoader.loadWorkflows(workflowDirLocation);

//        logger.info("Loaded unified intents cache with {} domain files.", fileToolIndex);
    }

    /**
     * Builds index mapping: filename (without extension) -> list of tool definitions.
     * Works with both classpath resources (JAR) and filesystem directories.
     */
    public Map<String, List<ToolDefinition>> buildFileToolIndex(String locationPattern) {
        Map<String, List<ToolDefinition>> index = new TreeMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            String pattern = locationPattern.endsWith("/")
                    ? locationPattern + "*.json"
                    : locationPattern + "/*.json";

            Resource[] resources = resolver.getResources(pattern);

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;

                String stem = filename.endsWith(".json")
                        ? filename.substring(0, filename.length() - 5)
                        : filename;

                try (InputStream is = resource.getInputStream()) {
                    JsonNode root = objectMapper.readTree(is);
                    JsonNode toolsNode = root.get("tools");

                    if (toolsNode != null && toolsNode.isArray()) {
                        List<ToolDefinition> entries = new ArrayList<>();

                        for (JsonNode tool : toolsNode) {
                            if (tool.isObject()) {
                                String name = tool.path("name").asText("").strip();
                                if (!name.isEmpty()) {
                                    String description = tool.path("description").asText("").strip();
                                    entries.add(new ToolDefinition(name, description, "function"));
                                }
                            }
                        }
                        index.put(stem, entries);
                    }
                } catch (Exception e) {
                    logger.warn("tool_file_parse_failed - file={}, error={}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read tool files from location: {}", locationPattern, e);
        }

        return Collections.unmodifiableMap(index);
    }

}