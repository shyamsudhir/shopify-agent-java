package com.app.supportspoc.controller;


import com.app.supportspoc.services.AgentServiceImpl;
import com.app.supportspoc.services.ClassifierServiceImpl;
import com.app.supportspoc.services.SessionStore;
import com.app.supportspoc.dto.ChatDto.ChatRequest;
import com.app.supportspoc.dto.ChatDto.ChatResponse;

import com.app.supportspoc.model.Session;
import com.app.supportspoc.exceptions.AIBackendNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);


    private final ClassifierServiceImpl classifierService;
    private final SessionStore sessionStore;
    private final AgentServiceImpl agentService;
    @Value("${shopify.mocked:false}")
    private boolean shopifyMocked;

    public ChatController(AgentServiceImpl agentService, ClassifierServiceImpl classifierService, SessionStore sessionStore) {
        this.agentService = agentService;
        this.classifierService = classifierService;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/chat")
    public CompletableFuture<ChatResponse> chat(@RequestBody ChatRequest body) {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("request_received at {}", startTime);
        logger.info("chat_turn_received - session_id={}, customer_email={}, cart_id={}, message_length={}",
                body.sessionId(), body.customerEmail(), body.cartId(), body.message().length());

        String sessionId = (body.sessionId() != null && !body.sessionId().isBlank())
                ? body.sessionId()
                : UUID.randomUUID().toString();

        Session session = sessionStore.getOrCreate(sessionId);
        if (body.customerEmail() != null) session.setCustomerEmail(body.customerEmail());
        if (body.cartId() != null) session.setCartId(body.cartId());

        return agentService.handleMessage(session, body.message())
                .thenApply(result -> new ChatResponse(
                        sessionId,
                        result.text(),
                        result.route().name(),
                        result.toolCalls()
                ))
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof AIBackendNotConfiguredException) {
                        logger.warn("chat_turn_unavailable - session_id={}, error={}", sessionId, cause.getMessage());
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "AI backend is not configured. Set OPENAI_API_KEY and retry.");
                    }

                    logger.error("chat_turn_failed - session_id={}, exception_type={}",
                            sessionId, cause.getClass().getSimpleName(), cause);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Something went wrong processing that message. Please try again.");
                });
    }

//    @PostMapping("/debug/route")
//    public CompletableFuture<ChatResponse.RouteDebugResponse> debugRoute(@RequestBody ChatResponse.RouteDebugRequest body) {
//        return classifierService.classify(body.message(), Collections.emptyList(), "")
//                .thenApply(result -> new ChatResponse.RouteDebugResponse(
//                        result.route().name(),
//                        result.isCompound(),
//                        result.tools(),
//                        result.kb(),
//                        result.reasoning(),
//                        result.entities()
//                ))
//                .exceptionally(ex -> {
//                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
//                    if (cause instanceof AIBackendNotConfiguredException) {
//                        logger.warn("debug_route_unavailable - error={}", cause.getMessage());
//                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
//                                "AI backend is not configured. Set OPENAI_API_KEY and retry.");
//                    }
//                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, cause.getMessage());
//                });
//    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "shopify_mocked", shopifyMocked);
    }
}