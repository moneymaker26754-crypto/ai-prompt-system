package com.jojo.prompt.integration.ai;

import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.integration.ai.dto.PythonOptimizeRequest;
import com.jojo.prompt.integration.ai.dto.PythonOptimizeStreamEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnProperty(
        prefix="app.ai",
        name="provider",
        havingValue="python"
)
public class PythonPromptAiStreamGateway implements PromptAiStreamGateway {

    private final WebClient webClient;
    public PythonPromptAiStreamGateway(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Flux<PromptAiStreamChunk> optimizeStream(PromptOptimizeRequestDTO dto, PromptTemplate template, String analysisResult) {

        PythonOptimizeRequest request = new PythonOptimizeRequest(
                dto.getOriginalPrompt(),
                analysisResult,
                template == null ? null : template.getOptimizeInstruction(),
                dto.getTarget(),
                dto.getOutputFormat(),
                template == null ? null : template.getSystemPrompt()
        );

        return webClient
                .post()
                .uri("/v1/prompts/optimize/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(PythonOptimizeStreamEvent.class)
                .map(event ->
                        new PromptAiStreamChunk(
                                event.type(),
                                event.content(),
                                event.model(),
                                event.code()
                        )
                );
    }
}
