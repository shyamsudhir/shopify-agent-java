package com.app.supportspoc.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the "$"-prefixed references used throughout the workflow DSL
 * (src/main/resources/workflows/*.json) and evaluates step "condition"
 * strings. Kept dependency-free and static so it's trivial to unit test.
 */
public final class ReferenceResolver {

    private static final Logger logger = LoggerFactory.getLogger(ReferenceResolver.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");

    private ReferenceResolver() {}

    /**
     * Resolves one raw value: a "$input.KEY" or "$stepId.path.to.field"
     * reference against ambient input / already-produced step outputs, or
     * returns the value unchanged if it isn't a reference (a literal).
     */
    public static Object resolve(Object rawValue, Map<String, Object> input, Map<String, Map<String, Object>> stepOutputs) {
        logger.info("Entering ReferenceResolver.resolve: rawValue={}, input={}, stepOutputs={}", rawValue, input, stepOutputs);
        if (!(rawValue instanceof String s) || !s.startsWith("$")) {
            return rawValue;
        }
        String[] parts = s.substring(1).split("\\.");
        if (parts.length == 0) return null;

        Object current = parts[0].equals("input") ? input : stepOutputs.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            current = navigate(current, parts[i]);
        }
        return current;
    }

    /**
     * Resolves "$result.PATH" against the raw result a step JUST produced.
     * Only valid inside that step's own "output" projection block.
     */
    public static Object resolveResultRef(Object rawValue, Object stepRawResult) {
        logger.info("Entering ReferenceResolver.resolveResultRef: rawValue={}, stepRawResult={}", rawValue, stepRawResult);
        if (!(rawValue instanceof String s) || !s.startsWith("$result")) {
            return rawValue;
        }
        String path = s.substring("$result".length());
        if (path.startsWith(".")) path = path.substring(1);
        Object current = stepRawResult;
        if (path.isEmpty()) return current;
        for (String part : path.split("\\.")) {
            if (current == null) break;
            current = navigate(current, part);
        }
        return current;
    }

    /** Resolves every value in a step's raw "input" map (step 10/11). */
    public static Map<String, Object> resolveAll(Map<String, Object> raw, Map<String, Object> input,
                                                   Map<String, Map<String, Object>> stepOutputs) {
        logger.info("Entering ReferenceResolver.resolveAll: raw={}, input={}, stepOutputs={}", raw, input, stepOutputs);
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (raw == null) return resolved;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getValue(), input, stepOutputs));
        }
        return resolved;
    }

    /**
     * Which other step ids (within the same workflow) a step's input/condition
     * refers to, i.e. its dependency edges for the DAG (step 13).
     */
    public static Set<String> referencedStepIds(Map<String, Object> rawInput, String condition, Set<String> knownStepIds) {
        logger.info("Entering ReferenceResolver.referencedStepIds: rawInput={}, condition={}, knownStepIds={}", rawInput, condition, knownStepIds);
        Set<String> refs = new LinkedHashSet<>();
        if (rawInput != null) {
            for (Object v : rawInput.values()) {
                if (v instanceof String s) collectRefs(s, knownStepIds, refs);
            }
        }
        if (condition != null) collectRefs(condition, knownStepIds, refs);
        return refs;
    }

    private static void collectRefs(String text, Set<String> knownStepIds, Set<String> out) {
        logger.info("Entering ReferenceResolver.collectRefs: text={}, knownStepIds={}", text, knownStepIds);
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            String root = matcher.group(1);
            if (knownStepIds.contains(root)) out.add(root);
        }
    }

    /**
     * Minimal evaluator for the condition shapes used in the DSL today:
     * "$ref != null", "$ref == null", or a bare reference treated as truthy.
     */
    public static boolean evaluateCondition(String condition, Map<String, Object> input,
                                             Map<String, Map<String, Object>> stepOutputs) {
        logger.info("Entering ReferenceResolver.evaluateCondition: condition={}, input={}, stepOutputs={}", condition, input, stepOutputs);
        if (condition == null || condition.isBlank()) return true;
        String trimmed = condition.trim();

        String operator = trimmed.contains("!=") ? "!=" : trimmed.contains("==") ? "==" : null;
        if (operator != null) {
            String[] sides = trimmed.split(Pattern.quote(operator), 2);
            Object left = resolveOperand(sides[0].trim(), input, stepOutputs);
            Object right = resolveOperand(sides[1].trim(), input, stepOutputs);
            boolean equal = Objects.equals(left, right);
            return operator.equals("!=") != equal;
        }
        return truthy(resolveOperand(trimmed, input, stepOutputs));
    }

    private static Object resolveOperand(String token, Map<String, Object> input, Map<String, Map<String, Object>> stepOutputs) {
        logger.info("Entering ReferenceResolver.resolveOperand: token={}, input={}, stepOutputs={}", token, input, stepOutputs);
        if (token.equals("null")) return null;
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            return token.substring(1, token.length() - 1);
        }
        if (token.startsWith("$")) return resolve(token, input, stepOutputs);
        return token;
    }

    private static boolean truthy(Object value) {
        logger.info("Entering ReferenceResolver.truthy: value={}", value);
        if (value == null) return false;
        if (value instanceof String s) return !s.isBlank();
        if (value instanceof Boolean b) return b;
        if (value instanceof java.util.Collection<?> c) return !c.isEmpty();
        if (value instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    private static Object navigate(Object current, String key) {
        logger.info("Entering ReferenceResolver.navigate: current={}, key={}", current, key);
        if (current instanceof Map<?, ?> map) return map.get(key);
        return null; // list indexing isn't used by the DSL samples in this repo today
    }
}
