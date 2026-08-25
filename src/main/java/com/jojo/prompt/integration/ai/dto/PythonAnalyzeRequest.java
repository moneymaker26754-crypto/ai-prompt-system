package com.jojo.prompt.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 将Java字段名映射到Python服务的JSON字段名
public record PythonAnalyzeRequest(

        @JsonProperty("original_prompt")
        String originalPrompt,

        @JsonProperty("system_prompt")
        String systemPrompt
) {
}
