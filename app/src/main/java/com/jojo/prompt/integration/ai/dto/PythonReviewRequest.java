package com.jojo.prompt.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonReviewRequest(

        @JsonProperty("original_prompt")
        String originalPrompt,

        @JsonProperty("optimized_prompt")
        String optimizedPrompt
) {
}
