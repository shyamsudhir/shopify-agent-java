package com.app.supportspoc.services;

import com.app.supportspoc.model.Session;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionStore {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId, Session::new);
    }

    public void clear() { sessions.clear(); }
}

