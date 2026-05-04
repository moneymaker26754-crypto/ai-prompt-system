package com.jojo.prompt.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.utils.PromptTemplateRenderUtil;
import com.jojo.prompt.dto.response.PromptOptimizeReviewResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PromptReviewAgent {

    private static final String REVIEW_PROMPT = """
            请审核优化后的提示词是否保持原意，并且只返回 JSON，不要返回 Markdown。
            JSON 格式：
            {
              "score": 0-100,
              "riskLevel": "LOW/MEDIUM/HIGH",
              "changedIntent": true/false,
              "reviewComment": "审核意见"
            }

            原始提示词：
            {{originalPrompt}}

            优化后提示词：
            {{optimizedPrompt}}
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Qualifier("promptOptimizeOllamaChatOptions")
    private final OllamaChatOptions promptOptimizeOllamaChatOptions;

    public PromptOptimizeReviewResult review(String originalPrompt, String optimizedPrompt) {
        String reviewPrompt = PromptTemplateRenderUtil.render(
                REVIEW_PROMPT,
                Map.of(
                        "originalPrompt", originalPrompt == null ? "" : originalPrompt,
                        "optimizedPrompt", optimizedPrompt == null ? "" : optimizedPrompt
                )
        );

        String content = chatClient.prompt()
                .options(promptOptimizeOllamaChatOptions)
                .user(reviewPrompt)
                .call()
                .content();

        try {
            return objectMapper.readValue(content, PromptOptimizeReviewResult.class);
        } catch (JsonProcessingException ex) {
            String normalized = normalizeJsonContent(content);
            if (StringUtils.hasText(normalized) && !normalized.equals(content)) {
                try {
                    return objectMapper.readValue(normalized, PromptOptimizeReviewResult.class);
                } catch (JsonProcessingException ignored) {
                    // Fall through and throw the business exception below.
                }
            }
            throw new BusinessException("review result parse failed");
        }
    }

    private String normalizeJsonContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }

        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return normalized.substring(start, end + 1);
        }
        return normalized;
    }
}
