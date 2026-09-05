package com.app.supportspoc.model;

/**
 * Step 18 of the runWithTools pipeline: every decomposed task must
 * eventually reach one of these states.
 */
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    BLOCKED,
    NEEDS_CUSTOMER_INPUT
}
