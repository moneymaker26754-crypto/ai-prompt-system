package com.jojo.prompt.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.integration.ai.dto.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
// 使用 @ConditionalOnProperty 只在配置 app.ai.provider=python 时激活
@ConditionalOnProperty(
        prefix = "app.ai",
        name = "provider",
        havingValue = "python"
)
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

        try {
            PythonAnalyzeResponse response =
                    restClient.post()
                            .uri("/v1/prompts/analyze")
                            .body(request)
                            .retrieve()
                            .body(PythonAnalyzeResponse.class);

            if(response == null || !StringUtils.hasText(response.analysis())) {
                throw new BusinessException("python ai analyze result is empty");
            }

            return new PromptAnalyzeResult(
                    response.analysis().trim(),
                    response.model()
            );
        } catch (RestClientResponseException ex) {
            throw mapRemoteException(ex);

        } catch (ResourceAccessException ex) {
            throw new BusinessException("python ai service unavailable: " + ex.getMessage());
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

        try {
            PythonOptimizeResponse response = restClient.post()
                    .uri("/v1/prompts/optimize")
                    .body(request)
                    .retrieve()
                    .body(PythonOptimizeResponse.class);

            if(response == null || !StringUtils.hasText(response.optimizedPrompt())){
                throw new BusinessException("python ai optimize result is empty");
            }

            return new PromptOptimizeResult(response.optimizedPrompt(), response.model());
        } catch(RestClientResponseException ex) {
            throw mapRemoteException(ex);
        } catch(ResourceAccessException ex) {
            throw new BusinessException("python ai service unavailable: " + ex.getMessage());
        }
    }

    private BusinessException mapRemoteException(RestClientResponseException ex) {

        try {
            PythonAiErrorResponse error = objectMapper.readValue(
                    ex.getResponseBodyAsString(),
                    PythonAiErrorResponse.class
            );

            return new BusinessException(
                    "python ai service error ["
                            + error.code()
                            + "]: "
                            + error.message()
            );
        } catch(Exception ignored) {
            return new BusinessException(
                    "python ai service returned HTTP "
                            + ex.getStatusCode().value()
            );
        }
    }
}
