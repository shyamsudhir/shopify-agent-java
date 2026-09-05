package com.app.supportspoc.services;

import com.app.supportspoc.dto.RetrievalResult;
import com.app.supportspoc.model.*;

import com.app.supportspoc.util.RagService;
import com.app.supportspoc.util.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class AgentServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(AgentServiceImpl.class);
    private static final int MAX_AGENT_ITERATIONS = 1;
    private static final Set<Route> TERMINAL_ROUTES = Set.of(
            Route.DIRECT,
            Route.UNSUPPORTED,
            Route.RAG
    );
    private static final Map<String, Route> ROUTE_MAP = Map.of(
            "DIRECT", Route.DIRECT,
            "RAG", Route.RAG,
            "TOOL", Route.TOOL,
            "TOOL_RAG", Route.TOOL_RAG,
            "MULTI_ACTION", Route.MULTI_ACTION,
            "UNSUPPORTED", Route.UNSUPPORTED
    );

    private final ClassifierServiceImpl classifierService;
    private final RagService ragService;
    private final ToolRunnerServiceImpl toolRunnerService;

    public AgentServiceImpl(
            ClassifierServiceImpl classifierService,
            RagService ragService,
            ToolRunnerServiceImpl toolRunnerService
    ) {
        this.classifierService = classifierService;
        this.ragService = ragService;
        this.toolRunnerService = toolRunnerService;
    }

    public CompletableFuture<AgentResponse> handleMessage(Session session, String message) {
        session.append("user", message);
        List<String> allToolCalls = new ArrayList<>();

        return executeLoop(session, message, 1, allToolCalls, Route.DIRECT, "", "")
                .thenApply(response -> {
                    // Persist the final assistant turn once after loop finishes
                    session.append("assistant", response.text());
                    logger.info(
                            "Turn completed | session_id={} | route={} | tools_count={}",
                            session.getSessionId(),
                            response.route(),
                            response.toolCalls().size()
                    );
                    return response;
                });
    }

    /**
     * Recursive asynchronous step loop replacing the Python for attempt loop.
     */
    private CompletableFuture<AgentResponse> executeLoop(
            Session session,
            String message,
            int attempt,
            List<String> allToolCalls,
            Route currentRoute,
            String currentText,
            String string) {
        if (attempt > MAX_AGENT_ITERATIONS) {
            return CompletableFuture.completedFuture(
                    new AgentResponse(currentText, currentRoute,  allToolCalls)
            );
        }

        long classStartNanos = System.nanoTime();
        logger.info("llm_call_before | name=classify | session_id={} | args={message={}, turns={}}",
                session.getSessionId(), message, session.getTurns());

        return classifierService.classify(message, session.getTurns())
                .thenCompose(classification -> {
                    double classDuration = (System.nanoTime() - classStartNanos) / 1_000_000_000.0;
                    logger.info("llm_call_after | name=classify | session_id={} | return={} | duration={}s",
                            session.getSessionId(), classification, classDuration);

                    logger.info("Classification completed in {} | session_id={} | route={} | attempt={} tasks={}",
                            classDuration, session.getSessionId(), classification.overallRoute(), attempt, classification.lines());

                    Route route = ROUTE_MAP.getOrDefault(classification.overallRoute(), Route.UNSUPPORTED);
                    List<ClassifierModels.ClassificationDecompositionOutput.LineItem> lines = classification.lines();

                    // Route dispatching
                    return executeRoute(session, message, classification, route)
                            .thenCompose(result -> {
                                logger.info("Route execution completed | session_id={} | route={} | attempt={} | tools_called={}",
                                        session.getSessionId(), route, attempt, result.toolCalls());
                                allToolCalls.addAll(result.toolCalls());
                                String stepText = result.text();

                                // Terminal routes exit on first try
                                if (TERMINAL_ROUTES.contains(route)) {
                                    return CompletableFuture.completedFuture(
                                            new AgentResponse(stepText, route,allToolCalls)
                                    );
                                }

                                // Tool routes exit if tools were called or iteration reached cap
                                if (!result.toolCalls().isEmpty() || attempt >= MAX_AGENT_ITERATIONS) {
                                    return CompletableFuture.completedFuture(
                                            new AgentResponse(stepText, route,allToolCalls)
                                    );
                                }

                                logger.warn(
                                        "Tool route yielded no tool calls on attempt {}. Retrying... | session_id={}",
                                        attempt, session.getSessionId()
                                );

                                // Recurse to attempt + 1
                                return executeLoop(session, message, attempt + 1, allToolCalls, route, stepText, "");
                            });
                });
    }

    private CompletableFuture<ToolExecutionResult> executeRoute(
            Session session,
            String message,
            ClassifierModels.ClassificationDecompositionOutput classification,
            Route route
    ) {
        logger.info("executeRoute | session_id={} | route={} | tasks={}",
                session.getSessionId(), route, classification.lines());
        return switch (route) {
            case DIRECT -> toolRunnerService.runDirect(session, Prompts.DIRECT_SYSTEM_PROMPT)
                    .thenApply(text -> new ToolExecutionResult(text, List.of()));

            case UNSUPPORTED -> toolRunnerService.runDirect(session, Prompts.UNSUPPORTED_SYSTEM_PROMPT)
                    .thenApply(text -> new ToolExecutionResult(text, List.of()));

            case RAG -> {
                RetrievalResult retrieval = ragService.retrieve(message);
                String systemPrompt = Prompts.RAG_SYSTEM_PROMPT.replace("{context}", retrieval.asContextBlock());
                yield toolRunnerService.runDirect(session, systemPrompt)
                        .thenApply(text -> new ToolExecutionResult(text, List.of()));
            }

            case TOOL_RAG -> {
                RetrievalResult retrieval = ragService.retrieve(message);
                String systemPrompt = Prompts.TOOL_RAG_SYSTEM_PROMPT.replace("{context}", retrieval.asContextBlock());
                yield toolRunnerService.runWithTools(session, classification);
            }

            case MULTI_ACTION -> toolRunnerService.runWithTools(session,  classification);

            case TOOL -> {
                yield toolRunnerService.runWithTools(session, classification);
            }
        };
    }
}