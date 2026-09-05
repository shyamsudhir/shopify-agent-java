package com.app.supportspoc.pipeline;

import com.app.supportspoc.model.WorkflowSpec;
import com.app.supportspoc.model.WorkflowSpec.StepSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * runWithTools steps 6-17. Given a workflow name and an input map, resolves
 * the workflow's steps as a DAG:
 *   - a step whose "input"/"condition" reference another step in the same
 *     workflow WAITS for that step to finish (step 13);
 *   - a step with no such reference starts as soon as its dependencies (if
 *     any, transitively) are ready, and runs CONCURRENTLY with any other
 *     step that's also ready (step 14);
 *   - a "workflow"-type step recurses into {@link #execute}, letting
 *     sub-workflows (ORDER_FETCHING, RETURN_POLICY_LOOKUP, ...) compose;
 *   - a "tool"-type step is deduplicated and dispatched through
 *     {@link ToolInvoker}, with successful results written into this
 *     workflow's step-output namespace (step 15);
 *   - a "resolve"-type step (no tool/workflow) is a placeholder entity
 *     resolution stage -- see {@link #resolveEntityStep}.
 */
@Component
public class WorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final Set<String> CREDENTIAL_KEYS = Set.of("shop_domain", "access_token");

    private final WorkflowRegistry workflowRegistry;
    private final ToolInvoker toolInvoker;

    public WorkflowEngine(WorkflowRegistry workflowRegistry, ToolInvoker toolInvoker) {
        this.workflowRegistry = workflowRegistry;
        this.toolInvoker = toolInvoker;
    }

    public CompletableFuture<WorkflowResult> execute(String workflowName, Map<String, Object> input, WorkflowExecutionContext ctx) {
        logger.info("Entering WorkflowEngine.execute: workflowName={}, input={}, ctx={}", workflowName, input, ctx);
        WorkflowSpec spec = workflowRegistry.find(workflowName);
        if (spec == null) {
            return CompletableFuture.completedFuture(WorkflowResult.failed("no workflow registered for '" + workflowName + "'"));
        }
        if (spec.steps() == null || spec.steps().isEmpty()) {
            return CompletableFuture.completedFuture(WorkflowResult.ok(Map.of()));
        }

        // Ambient values (customer_id, shop credentials, ...) are defaults: explicit
        // input passed by the caller (e.g. an entity extracted for this task) wins.
        Map<String, Object> effectiveInput = new LinkedHashMap<>(ctx.ambientInput());
        if (input != null) effectiveInput.putAll(input);

        Set<String> knownStepIds = spec.steps().stream().map(StepSpec::id).collect(Collectors.toSet());
        Map<String, Map<String, Object>> stepOutputs = new ConcurrentHashMap<>();
        Map<String, CompletableFuture<Map<String, Object>>> futures = new ConcurrentHashMap<>();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        for (StepSpec step : spec.steps()) {
            resolveStep(step, spec, effectiveInput, stepOutputs, futures, ctx, errors, knownStepIds);
        }

        return CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new))
                .thenApply(v -> {
                    if (!errors.isEmpty()) {
                        return WorkflowResult.failed(String.join("; ", errors));
                    }
                    Map<String, Object> output = ReferenceResolver.resolveAll(spec.output(), effectiveInput, stepOutputs);
                    return WorkflowResult.ok(output);
                });
    }

    /** Steps 11/13: recursively resolves a step's dependencies first, memoized per workflow-execution. */
    private CompletableFuture<Map<String, Object>> resolveStep(
            StepSpec step, WorkflowSpec spec, Map<String, Object> input,
            Map<String, Map<String, Object>> stepOutputs,
            Map<String, CompletableFuture<Map<String, Object>>> futures,
            WorkflowExecutionContext ctx, List<String> errors, Set<String> knownStepIds) {
        logger.info("Entering WorkflowEngine.resolveStep: step={}, spec={}, input={}, stepOutputs={}, knownStepIds={}",
                step != null ? step.id() : null, spec != null ? spec.workflow() : null, input, stepOutputs, knownStepIds);

        CompletableFuture<Map<String, Object>> existing = futures.get(step.id());
        if (existing != null) return existing;

        Set<String> dependsOn = ReferenceResolver.referencedStepIds(step.input(), step.condition(), knownStepIds);
        List<CompletableFuture<Map<String, Object>>> depFutures = new ArrayList<>();
        for (String depId : dependsOn) {
            findStep(spec, depId).ifPresent(depStep ->
                    depFutures.add(resolveStep(depStep, spec, input, stepOutputs, futures, ctx, errors, knownStepIds)));
        }

        CompletableFuture<Void> depsReady = depFutures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(depFutures.toArray(CompletableFuture[]::new));

        CompletableFuture<Map<String, Object>> future =
                depsReady.thenCompose(v -> runStep(step, input, stepOutputs, ctx, errors));

        futures.put(step.id(), future);
        return future;
    }

    /** Steps 10/12/14/15: executes one step once its dependencies are satisfied. */
    private CompletableFuture<Map<String, Object>> runStep(
            StepSpec step, Map<String, Object> input, Map<String, Map<String, Object>> stepOutputs,
            WorkflowExecutionContext ctx, List<String> errors) {
        logger.info("Entering WorkflowEngine.runStep: step={}, input={}, stepOutputs={}",
                step != null ? step.id() : null, input, stepOutputs);

        if (!ReferenceResolver.evaluateCondition(step.condition(), input, stepOutputs)) {
            stepOutputs.put(step.id(), Map.of());
            return CompletableFuture.completedFuture(Map.of());
        }

        Map<String, Object> resolvedInput = ReferenceResolver.resolveAll(step.input(), input, stepOutputs);

        if (step.isToolStep()) {
            return runToolStep(step, resolvedInput, stepOutputs, ctx, errors);
        }
        if (step.isWorkflowStep()) {
            return runWorkflowStep(step, resolvedInput, stepOutputs, ctx, errors);
        }
        Map<String, Object> resolved = resolveEntityStep(resolvedInput);
        stepOutputs.put(step.id(), resolved);
        return CompletableFuture.completedFuture(resolved);
    }

    private CompletableFuture<Map<String, Object>> runToolStep(
            StepSpec step, Map<String, Object> resolvedInput, Map<String, Map<String, Object>> stepOutputs,
            WorkflowExecutionContext ctx, List<String> errors) {
        logger.info("Entering WorkflowEngine.runToolStep: step={}, resolvedInput={}, stepOutputs={}",
                step != null ? step.id() : null, resolvedInput, stepOutputs);

        Map<String, Object> toolParams = new LinkedHashMap<>(resolvedInput);
        for (String key : CREDENTIAL_KEYS) {
            if (!toolParams.containsKey(key) && ctx.ambientInput().get(key) != null) {
                toolParams.put(key, ctx.ambientInput().get(key));
            }
        }

        // Step 12: identical tool+params -> one in-flight/completed call, shared across
        // every task's workflow this turn (WorkflowExecutionContext is request-scoped).
        String dedupKey = WorkflowExecutionContext.dedupKey(step.tool(), toolParams);
        CompletableFuture<Map<String, Object>> call = ctx.cachedOrCompute(dedupKey, () -> {
            long startedAt = System.currentTimeMillis();
            return toolInvoker.invoke(step.tool(), toolParams).whenComplete((result, ex) ->
                    ctx.record(new ToolCallTrace(step.tool(), toolParams, ex == null,
                            ex != null ? rootMessage(ex) : null, System.currentTimeMillis() - startedAt)));
        });

        return call.handle((result, ex) -> {
            if (ex != null) {
                errors.add(step.id() + " (" + step.tool() + "): " + rootMessage(ex));
                stepOutputs.put(step.id(), Map.of());
                return Map.<String, Object>of();
            }
            stepOutputs.put(step.id(), result);
            return result;
        });
    }

    private CompletableFuture<Map<String, Object>> runWorkflowStep(
            StepSpec step, Map<String, Object> resolvedInput, Map<String, Map<String, Object>> stepOutputs,
            WorkflowExecutionContext ctx, List<String> errors) {
        logger.info("Entering WorkflowEngine.runWorkflowStep: step={}, resolvedInput={}, stepOutputs={}",
                step != null ? step.id() : null, resolvedInput, stepOutputs);

        return execute(step.workflow(), resolvedInput, ctx).thenApply(subResult -> {
            Map<String, Object> published;
            if (!subResult.success()) {
                errors.add(step.id() + " (" + step.workflow() + "): " + subResult.error());
                published = Map.of();
            } else if (step.output() != null && !step.output().isEmpty()) {
                published = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : step.output().entrySet()) {
                    published.put(entry.getKey(), ReferenceResolver.resolveResultRef(entry.getValue(), subResult.output()));
                }
            } else {
                published = subResult.output();
            }
            stepOutputs.put(step.id(), published);
            return published;
        });
    }

    /**
     * Placeholder for steps 3/4 (contextual reference resolution) at the sub-workflow
     * level, e.g. ORDER_FETCHING's "resolve_reference" turning a raw order_reference
     * string into a canonical order_id. There's no fuzzy-matching/NLU service wired
     * for this yet (EntityDomainRegistry.findBestMatch is commented out), so for now
     * this passes every non-null input value through unchanged AND, when there's
     * exactly one unambiguous value, republishes it under the common id aliases a
     * downstream tool step is likely to expect. This keeps the DAG runnable
     * end-to-end; replace with a real resolver (order/product lookup by reference)
     * before relying on this for anything beyond a single, unambiguous reference.
     */
    private Map<String, Object> resolveEntityStep(Map<String, Object> resolvedInput) {
        logger.info("Entering WorkflowEngine.resolveEntityStep: resolvedInput={}", resolvedInput);
        Map<String, Object> out = new LinkedHashMap<>();
        resolvedInput.forEach((k, v) -> { if (v != null) out.put(k, v); });

        List<Object> nonNullValues = resolvedInput.values().stream().filter(Objects::nonNull).toList();
        if (nonNullValues.size() == 1) {
            Object soleValue = nonNullValues.get(0);
            for (String alias : List.of("order_id", "customer_id", "product_id", "variant_id", "cart_id")) {
                out.putIfAbsent(alias, soleValue);
            }
        }
        return out;
    }

    private static java.util.Optional<StepSpec> findStep(WorkflowSpec spec, String id) {
        logger.info("Entering WorkflowEngine.findStep: spec={}, id={}", spec != null ? spec.workflow() : null, id);
        return spec.steps().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    private static String rootMessage(Throwable ex) {
        logger.info("Entering WorkflowEngine.rootMessage: ex={}", ex != null ? ex.getMessage() : null);
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }
}
