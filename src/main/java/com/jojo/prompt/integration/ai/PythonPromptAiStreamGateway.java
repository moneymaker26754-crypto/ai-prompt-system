package com.jojo.prompt.integration.ai;

import com.jojo.prompt.common.filter.RequestIdFilter;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.integration.ai.dto.PythonOptimizeRequest;
import com.jojo.prompt.integration.ai.dto.PythonOptimizeStreamEvent;
import com.jojo.prompt.integration.ai.dto.PythonAiErrorResponse;
import com.jojo.prompt.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
@Slf4j
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

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        WebClient.RequestBodySpec requestSpec = webClient
                .post()
                .uri("/v1/prompts/optimize/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON);

        if (StringUtils.hasText(requestId)) {
            requestSpec.header(RequestIdFilter.HEADER, requestId);
        }

        return requestSpec
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response
                                .bodyToMono(PythonAiErrorResponse.class)
                                .map(error -> (Throwable) AiErrorMapping.fromCode(error.code()))
                                .switchIfEmpty(reactor.core.publisher.Mono.just(
                                        AiErrorMapping.fromCode("AI_UPSTREAM_ERROR")
                                ))
                                .onErrorReturn(AiErrorMapping.fromCode("AI_UPSTREAM_ERROR"))
                )
                .bodyToFlux(PythonOptimizeStreamEvent.class)
                .map(event ->
                        new PromptAiStreamChunk(
                                event.type(),
                                event.content(),
                                event.model(),
                                event.code()
                        )
                )
                .onErrorMap(
                        throwable -> !(throwable instanceof BusinessException),
                        throwable -> {
                            BusinessException mapped = AiErrorMapping.fromStreamFailure(throwable);
                            log.warn(
                                    "python ai stream failed, operation=optimize, errorCode={}, status=failed",
                                    mapped.getMessage()
                            );
                            return mapped;
                        }
                );
    }
}
