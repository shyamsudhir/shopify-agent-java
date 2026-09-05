package com.app.supportspoc.pipeline;

import java.util.Map;

public record WorkflowResult(Map<String, Object> output, boolean success, String error) {

    public static WorkflowResult ok(Map<String, Object> output) {
        return new WorkflowResult(output, true, null);
    }

    public static WorkflowResult failed(String error) {
        return new WorkflowResult(Map.of(), false, error);
    }
}
