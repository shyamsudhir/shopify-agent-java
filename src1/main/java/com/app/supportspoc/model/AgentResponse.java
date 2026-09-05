package com.app.supportspoc.model;

import java.util.List;

public record AgentResponse(
        String text,
        Route route,
        List<String> toolCalls
) {}