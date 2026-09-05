package com.app.supportspoc.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionState {
    // Context focus
    public String activeTopic = "GENERAL";
    public String activeId = "";

    // Typed Buckets (No raw Maps)
    public Map<String, OrderContext> orders = new HashMap<>();
    public Map<String, ProductContext> products = new HashMap<>();

    // Zero-boilerplate DTOs. This forces the JSON sent to the LLM to be tiny and focused.
    public record OrderContext(
            String orderName,
            String status,
            List<Product> items
    ) {}

    public record Product(
            String title,
            int quantity,
            String price
    ) {}

    public record ProductContext(
            String title,
            boolean inStock,
            String sizingNotes
    ) {}
}
