package com.jojo.prompt.service;

import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.PromptOptimizeStreamEvent;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface PromptOptimizationStreamService {
    Flux<ServerSentEvent<PromptOptimizeStreamEvent>> optimizeStream(PromptOptimizeRequestDTO dto);
}
