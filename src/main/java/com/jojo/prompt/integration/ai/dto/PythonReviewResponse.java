package com.jojo.prompt.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonReviewResponse(

        Integer score,

        @JsonProperty("risk_level")
        String riskLevel,

        @JsonProperty("changed_intent")
        Boolean changedIntent,

        @JsonProperty("review_comment")
        String reviewComment,

        String model
) {
}
