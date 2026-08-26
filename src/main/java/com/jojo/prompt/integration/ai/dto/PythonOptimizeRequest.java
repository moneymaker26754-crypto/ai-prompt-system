package com.jojo.prompt.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonOptimizeRequest(

        @JsonProperty("original_prompt")
        String originalPrompt,

        @JsonProperty("analysis_result")
        String analysisResult,

        String instruction,

        String target,

        @JsonProperty("output_format")
        String outputFormat,

        @JsonProperty("system_prompt")
        String systemPrompt
) {
}
