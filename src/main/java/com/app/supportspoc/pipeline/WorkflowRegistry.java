package com.app.supportspoc.pipeline;

import com.app.supportspoc.model.WorkflowSpec;
import com.app.supportspoc.util.ToolIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 6 (resolve intent -> workflow): merges every workflow file under
 * src/main/resources/workflows/ into a single WORKFLOW_NAME -> WorkflowSpec
 * index. Files are loaded once at startup by ToolIndexService/IntentRegistryLoader;
 * this just gives them a typed, name-addressable shape for the WorkflowEngine.
 */
@Component
public class WorkflowRegistry {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowRegistry.class);

    private final ToolIndexService toolIndexService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WorkflowSpec> workflowsByName = new LinkedHashMap<>();

    public WorkflowRegistry(ToolIndexService toolIndexService) {
        this.toolIndexService = toolIndexService;
    }

    @PostConstruct
    public void init() {
        Map<String, Object> rawByFile = toolIndexService.getWorkflows();
        if (rawByFile == null) return;

        for (Map.Entry<String, Object> fileEntry : rawByFile.entrySet()) {
            String filename = fileEntry.getKey();
            Object parsed = fileEntry.getValue();
            if (!(parsed instanceof Map<?, ?> topLevel)) continue;

            for (Map.Entry<?, ?> entry : topLevel.entrySet()) {
                String key = String.valueOf(entry.getKey());
                try {
                    WorkflowSpec spec = objectMapper.convertValue(entry.getValue(), WorkflowSpec.class);
                    if (spec == null) continue;
                    // register under both the file's top-level key and the spec's own
                    // "workflow" field, defensively, in case they ever diverge.
                    workflowsByName.put(key, spec);
                    if (spec.workflow() != null && !spec.workflow().equals(key)) {
                        workflowsByName.put(spec.workflow(), spec);
                    }
                } catch (Exception e) {
                    logger.warn("workflow_parse_failed - file={}, entry={}, error={}", filename, key, e.getMessage());
                }
            }
        }
        logger.info("Loaded {} workflow definitions from {} files.", workflowsByName.size(), rawByFile.size());
    }

    public WorkflowSpec find(String workflowName) {
        return workflowName != null ? workflowsByName.get(workflowName) : null;
    }

    public boolean contains(String workflowName) {
        return workflowName != null && workflowsByName.containsKey(workflowName);
    }
}
