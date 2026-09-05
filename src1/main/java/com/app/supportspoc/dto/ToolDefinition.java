package com.app.supportspoc.dto;

public record ToolDefinition(
        String name,
        String description,
        String type
) {
    public ToolDefinition(String name, String description) {
        this(name, description, "function");
    }

    public String toString() {
        return "ToolDefinition{name='" + name + "', description='" + description + "', type='" + type + "'}";
    }
}