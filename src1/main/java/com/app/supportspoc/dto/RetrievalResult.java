package com.app.supportspoc.dto;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RetrievalResult(
        List<DocumentChunk> documents,
        String query
) {


    public record DocumentChunk(
            String id,
            String title,
            String content,
            double score,
            Map<String, Object> metadata
    ) {
        public DocumentChunk(String id, String title, String content, double score) {
            this(id, title, content, score, Collections.emptyMap());
        }
    }

    public RetrievalResult {
        if (documents == null) {
            documents = Collections.emptyList();
        }
    }

    /**
     * Formats retrieved document chunks into a structured context block
     * ready for injection into RAG system prompts.
     */
    public String asContextBlock() {
        if (documents.isEmpty()) {
            return "(No relevant knowledge base articles found.)";
        }

        return documents.stream()
                .map(doc -> {
                    String titleHeader = (doc.title() != null && !doc.title().isBlank())
                            ? "### Source: " + doc.title() + "\n"
                            : "";
                    return titleHeader + doc.content();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * Factory helper for empty retrieval scenarios.
     */
    public static RetrievalResult empty(String query) {
        return new RetrievalResult(Collections.emptyList(), query);
    }
}