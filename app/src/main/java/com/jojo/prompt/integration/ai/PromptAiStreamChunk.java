package com.jojo.prompt.integration.ai;

public record PromptAiStreamChunk(
        String type,
        String content,
        String model,
        String code
) {
}
