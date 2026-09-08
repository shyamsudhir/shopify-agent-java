package com.app.supportspoc.slotfill;

import java.util.Map;

public class SlotFillingDto {

    private SlotFillingDto() {}

    public record SlotFillingRequest(String sessionId, String message) {}

    public record SlotFillingResponse(
            String sessionId,
            String reply,
            String status,
            String intent,
            Map<String, Object> collectedEntities,
            Map<String, Object> result
    ) {}
}
