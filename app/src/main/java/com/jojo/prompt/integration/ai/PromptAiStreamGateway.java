package com.jojo.prompt.integration.ai;

import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.entity.PromptTemplate;
import reactor.core.publisher.Flux;

public interface PromptAiStreamGateway {

    Flux<PromptAiStreamChunk> optimizeStream(
            PromptOptimizeRequestDTO request,
            PromptTemplate template,
            String analysisResult
    );
}
