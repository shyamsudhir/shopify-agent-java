package com.app.supportspoc.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * One instance per customer turn, shared by every task's workflow
 * execution so that:
 *   - step 12 (dedup) works ACROSS tasks, not just within one workflow --
 *     the same tool+params called from two different tasks' workflows
 *     resolves to the same in-flight/completed future.
 *   - step 19/21 have a single trace of every tool call made this turn,
 *     for logging, the toolCalls list on the response, and persistence.
 */
public final class WorkflowExecutionContext {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutionContext.class);

    private final Map<String, CompletableFuture<Map<String, Object>>> toolCallCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ToolCallTrace> trace = new ConcurrentLinkedQueue<>();

    /** Ambient values available to every task's workflow input (customer_id, email, active order/product, ...). */
    private final Map<String, Object> ambientInput;

    public WorkflowExecutionContext(Map<String, Object> ambientInput) {
        logger.info("Initializing WorkflowExecutionContext: ambientInput={}", ambientInput);
        this.ambientInput = ambientInput;
    }

    public Map<String, Object> ambientInput() {
        logger.info("Entering WorkflowExecutionContext.ambientInput");
        return ambientInput;
    }

    /** Dedup key: same tool name + same params -> same call (step 12). */
    public static String dedupKey(String toolName, Map<String, Object> params) {
        logger.info("Entering WorkflowExecutionContext.dedupKey: toolName={}, params={}", toolName, params);
        return toolName + "|" + params;
    }

    public CompletableFuture<Map<String, Object>> cachedOrCompute(
            String dedupKey, java.util.function.Supplier<CompletableFuture<Map<String, Object>>> supplier) {
        logger.info("Entering WorkflowExecutionContext.cachedOrCompute: dedupKey={}", dedupKey);
        return toolCallCache.computeIfAbsent(dedupKey, k -> supplier.get());
    }

    public void record(ToolCallTrace entry) {
        logger.info("Entering WorkflowExecutionContext.record: entry={}", entry);
        trace.add(entry);
    }

    public List<ToolCallTrace> trace() {
        logger.info("Entering WorkflowExecutionContext.trace");
        return List.copyOf(trace);
    }
}
