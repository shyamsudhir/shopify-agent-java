package com.app.supportspoc.slotfill;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plain data types shared across the slot-filling module. Kept dependency-free
 * (no Spring, no I/O) so the schema/state shapes are trivial to unit test.
 */
public final class SlotFillingModels {

    private SlotFillingModels() {}

    /**
     * One entity slot an intent may need.
     *
     * @param required     whether slot-filling must block on this value before the intent can execute
     * @param defaultValue applied automatically when the slot is optional and was never provided
     *                     (e.g. a fixed constant, or a sane system default a customer would never
     *                     naturally be asked for); null means "just leave it absent"
     */
    public record EntitySpec(String name, String type, String description, boolean required, Object defaultValue) {

        public EntitySpec(String name, String type, String description, boolean required) {
            this(name, type, description, required, null);
        }
    }

    /** One intent's full schema: what it does and what it needs to run. */
    public record IntentSpec(String name, String description, List<EntitySpec> entities) {

        public List<EntitySpec> requiredEntities() {
            return entities.stream().filter(EntitySpec::required).toList();
        }

        public Optional<EntitySpec> entity(String name) {
            return entities.stream().filter(e -> e.name().equals(name)).findFirst();
        }
    }

    /** Where a conversation stands relative to whichever intent is currently active. */
    public enum ConversationStatus { COLLECTING, READY_TO_EXECUTE, EXECUTED }

    /** One turn of conversation history, kept only as classifier prompt context. */
    public record Turn(String role, String content) {}

    /**
     * Structured output the classifier LLM call returns for the latest user message: which
     * intent it belongs to (or "unknown"), and whatever entity values it could confidently
     * read off that one message.
     */
    public record IntentClassificationResult(String intent, Map<String, Object> entities) {}
}
