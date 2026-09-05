package com.app.supportspoc.pipeline;

import java.util.Map;

public record ToolCallTrace(
        String toolName,
        Map<String, Object> params,
        boolean success,
        String error,
        long tookMillis
) {
    @Override
    public String toString() {
        return success
                ? toolName + "(" + params + ")"
                : toolName + "(" + params + ") FAILED: " + error;
    }
}
