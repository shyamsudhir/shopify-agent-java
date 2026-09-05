package com.app.supportspoc.pipeline;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Executes one tool call named in the workflow DSL (e.g. "shopify.get_order").
 * Implementations must never fabricate data: a tool that isn't wired to a
 * real backend should fail/flag rather than return invented values.
 */
@FunctionalInterface
public interface ToolInvoker {
    CompletableFuture<Map<String, Object>> invoke(String toolName, Map<String, Object> params);
}
