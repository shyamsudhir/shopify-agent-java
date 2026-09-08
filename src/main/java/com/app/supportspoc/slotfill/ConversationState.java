package com.app.supportspoc.slotfill;

import com.app.supportspoc.slotfill.SlotFillingModels.ConversationStatus;
import com.app.supportspoc.slotfill.SlotFillingModels.Turn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-conversation slot-filling state: which intent is currently being filled, what's
 * been collected for it so far, and the running turn history used as classifier context.
 * One instance per sessionId, owned and mutated only through ConversationStateManager.
 */
public final class ConversationState {

    private final String sessionId;
    private final Map<String, Object> collectedEntities = new LinkedHashMap<>();
    private final List<Turn> history = new ArrayList<>();

    private String currentIntent;
    private ConversationStatus status;
    private String lastExecutedIntent;
    private Map<String, Object> lastResult;

    public ConversationState(String sessionId) {
        this.sessionId = sessionId;
    }

    public synchronized void appendTurn(String role, String content) {
        if (content != null) history.add(new Turn(role, content));
    }

    public synchronized List<Turn> history() {
        return List.copyOf(history);
    }

    /** Starts (or restarts) slot-filling for a new intent, discarding any partially-collected entities. */
    public synchronized void startIntent(String intentName) {
        this.currentIntent = intentName;
        this.status = ConversationStatus.COLLECTING;
        this.collectedEntities.clear();
    }

    public synchronized void put(String key, Object value) {
        if (value != null) collectedEntities.put(key, value);
    }

    public synchronized boolean has(String key) {
        return collectedEntities.get(key) != null;
    }

    public synchronized Map<String, Object> collectedEntities() {
        return new LinkedHashMap<>(collectedEntities);
    }

    public synchronized void markReadyToExecute() {
        this.status = ConversationStatus.READY_TO_EXECUTE;
    }

    /** Records the outcome and clears active-intent state so the next message starts fresh. */
    public synchronized void completeExecution(Map<String, Object> result) {
        this.status = ConversationStatus.EXECUTED;
        this.lastExecutedIntent = this.currentIntent;
        this.lastResult = result;
        this.currentIntent = null;
        this.collectedEntities.clear();
    }

    public String sessionId() { return sessionId; }
    public synchronized String currentIntent() { return currentIntent; }
    public synchronized ConversationStatus status() { return status; }
    public synchronized String lastExecutedIntent() { return lastExecutedIntent; }
    public synchronized Map<String, Object> lastResult() { return lastResult; }
}
