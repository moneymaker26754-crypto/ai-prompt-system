package com.jojo.prompt.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.integration.ai.dto.PythonRagComparisonResponse;
import com.jojo.prompt.integration.ai.dto.PythonRagEvaluationRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PythonRagEvaluationGateway implements RagEvaluationGateway {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-API-Key";

    private final RestClient restClient;
    private final AiServiceProperties properties;
    private final ObjectMapper objectMapper;

    public PythonRagEvaluationGateway(
            @Qualifier("pythonRagRestClient") RestClient restClient,
            AiServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PythonRagComparisonResponse compareExactAndHnsw(String dataset) {
        try {
            PythonRagComparisonResponse response = restClient.post()
                    .uri("/internal/rag/evaluate/compare")
                    .header(INTERNAL_API_KEY_HEADER, properties.getInternalApiKey())
                    .body(new PythonRagEvaluationRequest(dataset))
                    .retrieve()
                    .body(PythonRagComparisonResponse.class);

            if (response == null || response.exact() == null || response.hnsw() == null) {
                throw new BusinessException(502, "INVALID_RAG_EVALUATION_RESPONSE");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw mapRemoteException(exception);
        } catch (ResourceAccessException exception) {
            throw AiErrorMapping.fromTransport(exception);
        }
    }

    private BusinessException mapRemoteException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 409) {
            return new BusinessException(409, "RAG_HNSW_INDEX_MISSING");
        }
        if (status == 400 || status == 404 || status == 422) {
            return new BusinessException(400, "RAG_EVALUATION_INVALID_REQUEST");
        }
        if (status == 401) {
            return new BusinessException(502, "RAG_UPSTREAM_AUTH_ERROR");
        }

        try {
            var error = objectMapper.readValue(
                    exception.getResponseBodyAsString(),
                    com.jojo.prompt.integration.ai.dto.PythonAiErrorResponse.class
            );
            return AiErrorMapping.fromCode(error.code());
        } catch (Exception ignored) {
            return new BusinessException(502, "RAG_EVALUATION_UPSTREAM_ERROR");
        }
    }
}
