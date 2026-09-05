package com.app.supportspoc.model;

import java.util.List;

public record ToolExecutionResult(
        String text,
        List<String> toolCalls
) {}