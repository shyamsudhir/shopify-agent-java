package com.app.supportspoc.model;


import com.app.supportspoc.dto.SessionState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Session {
    private final String sessionId;
    private String customerEmail;
    private String cartId;
    private final List<Turn> turns = new ArrayList<>();
    private SessionState sessionState = new SessionState();
    public void addHistory(Object tasks) {

    }

    public CharSequence getHistory() {
        return null;
    }

    public void addStateTransition(ToolModel.StateTransition stateTransition) {
    }

    public Collection<Object> getStateMachine() {
        return List.of();
    }

    public record Turn(String role, String content) {}

    public Session(String sessionId) {
        this.sessionId = sessionId;
    }

    public synchronized void append(String role, String content) {
        turns.add(new Turn(role, content));
    }

    public synchronized List<Turn> getTurns() {
        return Collections.unmodifiableList(new ArrayList<>(turns));
    }

    public String getSessionId() { return sessionId; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }
    public void setSessionState(SessionState sessionState) { this.sessionState = sessionState; }
    public SessionState getSessionState() { return sessionState; }
}