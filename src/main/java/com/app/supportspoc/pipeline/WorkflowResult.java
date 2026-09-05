package com.app.supportspoc.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public record WorkflowResult(Map<String, Object> output, boolean success, String error) {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowResult.class);

    public static WorkflowResult ok(Map<String, Object> output) {
        logger.info("Entering WorkflowResult.ok: output={}", output);
        return new WorkflowResult(output, true, null);
    }

    public static WorkflowResult failed(String error) {
        logger.info("Entering WorkflowResult.failed: error={}", error);
        return new WorkflowResult(Map.of(), false, error);
    }
}
