package com.app.supportspoc.dto;


import java.util.List;
import java.util.Map;

public class ChatDto {


    public record ChatRequest(
            String sessionId,

//            @NotBlank(message = "Message cannot be empty")
//            @Size(min = 1, max = 4000, message = "Message length must be between 1 and 4000")
            String message,
            String customerEmail,
            String cartId
    ) {
    }

    public record ChatResponse(
            String sessionId,
            String reply,
            String route,
            List<String> toolsUsed
    ) {
        static String message;

        public record RouteDebugRequest(
                String message
        ) {
        }

        public record RouteDebugResponse(
                String category,
                String route,
                boolean isMultiAction,
                List<String> tools,
                List<String> kb,
                String reasoning,
                Map<String, Object> entities
        ) {
        }
    }
}