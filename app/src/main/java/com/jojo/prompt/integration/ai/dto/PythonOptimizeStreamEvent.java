package com.jojo.prompt.integration.ai.dto;

public record PythonOptimizeStreamEvent(
        String type,
        String content,
        String model,
        String code
) {
}
