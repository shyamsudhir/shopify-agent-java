package com.app.supportspoc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Typed view of one workflow entry from src/main/resources/workflows/*.json,
 * e.g.:
 *
 *   "RETURN_ELIGIBILITY": {
 *     "workflow": "RETURN_ELIGIBILITY",
 *     "input": { "customer_id": "required", "order_reference": "required" },
 *     "steps": [
 *       { "id": "resolve_order", "workflow": "ORDER_FETCHING",
 *         "input": {...}, "output": {"order": "$result.order"} },
 *       { "id": "load_policy", "workflow": "RETURN_POLICY_LOOKUP",
 *         "input": {"order": "$resolve_order.order"}, "output": {...} }
 *     ],
 *     "output": { "eligible": "$evaluate.eligible" }
 *   }
 *
 * Step input/output values are either literal values or "$"-prefixed
 * references (see {@link com.app.supportspoc.pipeline.ReferenceResolver}):
 *   - "$input.KEY"      -> a value from this workflow's own input
 *   - "$STEP_ID.PATH"   -> a (possibly nested) field from another step's
 *                          already-produced output, in the SAME workflow
 *   - "$result.PATH"    -> (only inside a step's own "output" map) a field
 *                          of the raw result THIS step just produced, used
 *                          to project a sub-workflow's output into this
 *                          step's local namespace
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowSpec(
        String workflow,
        String description,
        Map<String, Object> input,
        List<StepSpec> steps,
        Map<String, Object> output
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepSpec(
            String id,
            String tool,
            String workflow,
            String type,
            Map<String, Object> input,
            Map<String, Object> output,
            String condition
    ) {
        public boolean isToolStep() { return tool != null && !tool.isBlank(); }
        public boolean isWorkflowStep() { return workflow != null && !workflow.isBlank(); }
        public boolean isResolveStep() { return !isToolStep() && !isWorkflowStep(); }
    }
}
