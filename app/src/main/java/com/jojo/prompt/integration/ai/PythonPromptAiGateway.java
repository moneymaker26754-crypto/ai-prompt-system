package com.jojo.prompt.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.filter.RequestIdFilter;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.integration.ai.dto.*;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class PythonPromptAiGateway implements PromptAiGateway {

    // 通过 RestClient 发送HTTP请求到Python服务
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PythonPromptAiGateway(
            @Qualifier("pythonAiRestClient")
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PromptAnalyzeResult analyze(String originalPrompt, PromptTemplate template) {

        PythonAnalyzeRequest request =
                new PythonAnalyzeRequest(
                        originalPrompt,
                        template == null ? null : template.getSystemPrompt()
                );

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        try {
            PythonAnalyzeResponse response =
                    restClient.post()
                             .uri("/v1/prompts/analyze")
                            .headers(headers -> addRequestId(headers, requestId))
                            .body(request)
                            .retrieve()
                            .body(PythonAnalyzeResponse.class);

            if(response == null || !StringUtils.hasText(response.analysis())) {
                throw AiErrorMapping.fromCode("INVALID_MODEL_OUTPUT");
            }

            return new PromptAnalyzeResult(
                    response.analysis().trim(),
                    response.model()
            );
        } catch (RestClientResponseException ex) {
            throw mapRemoteException(ex, "analyze");

        } catch (ResourceAccessException ex) {
            throw mapTransportException(ex, "analyze");
        }
    }

    @Override
    public PromptOptimizeResult optimize(PromptOptimizeRequestDTO dto, PromptTemplate template, String analysisResult) {

        PythonOptimizeRequest request = new PythonOptimizeRequest(
                dto.getOriginalPrompt(),
                analysisResult,
                template == null ? null : template.getOptimizeInstruction(),
                dto.getTarget(),
                dto.getOutputFormat(),
                template == null ? null : template.getSystemPrompt()

        );

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        try {
            PythonOptimizeResponse response = restClient.post()
                    .uri("/v1/prompts/optimize")
                    .headers(headers -> addRequestId(headers, requestId))
                    .body(request)
                    .retrieve()
                    .body(PythonOptimizeResponse.class);

            if(response == null || !StringUtils.hasText(response.optimizedPrompt())){
                throw AiErrorMapping.fromCode("INVALID_MODEL_OUTPUT");
            }

            return new PromptOptimizeResult(response.optimizedPrompt(), response.model());
        } catch(RestClientResponseException ex) {
            throw mapRemoteException(ex, "optimize");
        } catch(ResourceAccessException ex) {
            throw mapTransportException(ex, "optimize");
        }
    }

    @Override
    public PromptReviewResult review(String originalPrompt, String optimizedPrompt) {

        PythonReviewRequest request = new PythonReviewRequest(originalPrompt, optimizedPrompt);

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        try {
            PythonReviewResponse response = restClient.post()
                    .uri("/v1/prompts/review")
                    .headers(headers -> addRequestId(headers, requestId))
                    .body(request)
                    .retrieve()
                    .body(PythonReviewResponse.class);

            if(response == null) {
                throw AiErrorMapping.fromCode("INVALID_MODEL_OUTPUT");
            }
            validateReviewResponse(response);

            return new PromptReviewResult(
                    response.score(),
                    response.riskLevel(),
                    response.changedIntent(),
                    response.reviewComment(),
                    response.model()
            );
        } catch (RestClientResponseException ex) {
            throw mapRemoteException(ex, "review");
        } catch (ResourceAccessException ex) {
            throw mapTransportException(ex, "review");
        }
    }

    private BusinessException mapRemoteException(
            RestClientResponseException ex,
            String operation
    ) {

        try {
            PythonAiErrorResponse error = objectMapper.readValue(
                    ex.getResponseBodyAsString(),
                    PythonAiErrorResponse.class
            );

            BusinessException mapped = AiErrorMapping.fromCode(error.code());
            log.warn(
                    "python ai request failed, operation={}, errorCode={}, httpStatus={}, status=failed",
                    operation,
                    mapped.getMessage(),
                    ex.getStatusCode().value()
            );
            return mapped;
        } catch(Exception ignored) {
            log.warn(
                    "python ai response could not be mapped, operation={}, httpStatus={}, status=failed",
                    operation,
                    ex.getStatusCode().value()
            );
            return AiErrorMapping.fromCode("AI_UPSTREAM_ERROR");
        }
    }

    private BusinessException mapTransportException(
            ResourceAccessException ex,
            String operation
    ) {
        BusinessException mapped = AiErrorMapping.fromTransport(ex);
        log.warn(
                "python ai transport failed, operation={}, errorCode={}, status=failed",
                operation,
                mapped.getMessage()
        );
        return mapped;
    }

    private void addRequestId(
            org.springframework.http.HttpHeaders headers,
            String requestId
    ) {
        if (StringUtils.hasText(requestId)) {
            headers.set(RequestIdFilter.HEADER, requestId);
        }
    }

    private void validateReviewResponse(
            PythonReviewResponse response
    ) {

        if (
                response.score() == null
                        || response.score() < 0
                        || response.score() > 100
        ) {
            throw AiErrorMapping.fromCode("INVALID_MODEL_OUTPUT");
        }

        if (
                !"LOW".equals(response.riskLevel())
                        && !"MEDIUM".equals(response.riskLevel())
                        && !"HIGH".equals(response.riskLevel())
        ) {
            throw AiErrorMapping.fromCode("INVALID_MODEL_OUTPUT");
        }
    }
}
