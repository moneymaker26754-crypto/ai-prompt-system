package com.jojo.prompt.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonOptimizeResponse(

        @JsonProperty("optimized_prompt")
        String optimizedPrompt,

        String model
) {
}
