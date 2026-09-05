package com.app.supportspoc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

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
                @Getter
                String intent,
                Route route
        ) {}

        public record SubIntentLineItem(
                @JsonProperty("line_number")
                int lineNumber,
                String text,
                @Getter
                List<String> intents,
                Route route
        ) {}
    }

}