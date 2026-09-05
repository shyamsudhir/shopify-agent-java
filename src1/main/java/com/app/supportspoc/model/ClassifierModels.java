package com.app.supportspoc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ClassifierModels {

    public record ClassificationDecompositionOutput(
            @JsonProperty("is_compound") boolean isCompound,
            @JsonProperty("overall_route") String overallRoute,
            String reasoning,
            List<LineItem> lines
    ) {
        public record LineItem(
                @JsonProperty("line_number")
                int lineNumber,
                String text,
                List<String> intents,
                Route route
        ) {}
    }

}