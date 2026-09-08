package com.app.supportspoc.slotfill;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-session store for slot-filling ConversationState -- mirrors the shape of
 * SessionStore used by the main chat pipeline. State does not survive an app restart;
 * swap the backing map for a persistent store if that's ever required.
 */
@Component
public class ConversationStateManager {

    private final Map<String, ConversationState> states = new ConcurrentHashMap<>();

    public ConversationState getOrCreate(String sessionId) {
        return states.computeIfAbsent(sessionId, ConversationState::new);
    }

    public void reset(String sessionId) {
        states.remove(sessionId);
    }
}
