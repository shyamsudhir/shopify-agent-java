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

    // Set once per-session OAuth/token-exchange (see ShopifyTokenExchangeService) is
    // wired into the chat flow; until then ShopifyToolInvoker falls back to the
    // single-tenant app.shopify.* properties when these are null.
    private String shopDomain;
    private String shopAccessToken;

    private final List<Turn> turns = new ArrayList<>();
    private SessionState sessionState = new SessionState();

    // Step 21 (persist final session state): a running log of pipeline-level events
    // (e.g. completed PipelineTask summaries) distinct from the raw conversation
    // turns below.
    private final List<Object> history = new ArrayList<>();
    private final List<ToolModel.StateTransition> stateMachine = new ArrayList<>();

    public synchronized void addHistory(Object entry) {
        if (entry != null) history.add(entry);
    }

    /** Plain-text rendering of the conversation turns so far, for prompts that want raw history. */
    public synchronized CharSequence getHistory() {
        if (turns.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Turn turn : turns) {
            sb.append(turn.role()).append(": ").append(turn.content()).append('\n');
        }
        return sb;
    }

    public synchronized void addStateTransition(ToolModel.StateTransition stateTransition) {
        if (stateTransition != null) stateMachine.add(stateTransition);
    }

    public synchronized Collection<Object> getStateMachine() {
        return Collections.unmodifiableList(new ArrayList<>(stateMachine));
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
    public String getShopDomain() { return shopDomain; }
    public void setShopDomain(String shopDomain) { this.shopDomain = shopDomain; }
    public String getShopAccessToken() { return shopAccessToken; }
    public void setShopAccessToken(String shopAccessToken) { this.shopAccessToken = shopAccessToken; }
}
