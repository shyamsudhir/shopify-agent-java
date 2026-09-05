package com.app.supportspoc.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One atomic task produced by decomposing the customer's request
 * (runWithTools step 2), carrying the entities extracted for it
 * (steps 3-4) through workflow resolution (step 6) and execution
 * (steps 7-17) to a terminal outcome (step 18).
 */
public final class PipelineTask {

    private final String id;
    private final ToolModel.IntentClassification classification;
    private String workflowName;
    private TaskStatus status = TaskStatus.PENDING;
    private String blockReason;
    private final Map<String, Object> results = new LinkedHashMap<>();

    public PipelineTask(String id, ToolModel.IntentClassification classification) {
        this.id = id;
        this.classification = classification;
    }

    public String id() { return id; }

    public String intent() { return classification.intent(); }

    public List<ToolModel.ExtractedEntity> entities() {
        return classification.entities() != null ? classification.entities() : List.of();
    }

    public String originalTask() { return classification.originalTask(); }

    public String workflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public TaskStatus status() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public String blockReason() { return blockReason; }

    public void block(TaskStatus status, String reason) {
        this.status = status;
        this.blockReason = reason;
    }

    public Map<String, Object> results() { return results; }

    public boolean isTerminal() {
        return status == TaskStatus.COMPLETED
                || status == TaskStatus.FAILED
                || status == TaskStatus.BLOCKED
                || status == TaskStatus.NEEDS_CUSTOMER_INPUT;
    }

    @Override
    public String toString() {
        return "PipelineTask{id=" + id + ", intent=" + intent() + ", status=" + status
                + (blockReason != null ? ", reason=" + blockReason : "") + "}";
    }
}
