package com.app.supportspoc.slotfill;

import com.app.supportspoc.slotfill.SlotFillingDto.SlotFillingRequest;
import com.app.supportspoc.slotfill.SlotFillingDto.SlotFillingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Standalone demo endpoint for the 5-intent slot-filling agent. Deliberately separate
 * from /chat (ChatController) so this module stays independently testable without
 * touching the existing classification/workflow pipeline.
 */
@RestController
public class SlotFillingController {

    private static final Logger logger = LoggerFactory.getLogger(SlotFillingController.class);

    private final SlotFillingConversationHandler handler;

    public SlotFillingController(SlotFillingConversationHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/slotfill/chat")
    public CompletableFuture<SlotFillingResponse> chat(@RequestBody SlotFillingRequest request) {
        String sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId()
                : UUID.randomUUID().toString();

        logger.info("slotfill_turn_received - session_id={}, message_length={}",
                sessionId, request.message() == null ? 0 : request.message().length());

        return handler.handleMessage(sessionId, request.message())
                .thenApply(result -> new SlotFillingResponse(
                        result.sessionId(),
                        result.reply(),
                        result.status() == null ? null : result.status().name(),
                        result.intent(),
                        result.collectedEntities(),
                        result.result()
                ));
    }
}
