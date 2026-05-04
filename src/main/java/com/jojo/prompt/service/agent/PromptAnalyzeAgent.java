package com.jojo.prompt.service.agent;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.utils.PromptTemplateRenderUtil;
import com.jojo.prompt.entity.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PromptAnalyzeAgent {

    private static final String ANALYZE_PROMPT = """
            请分析以下提示词，并从表达清晰度、约束条件、输出格式、上下文完整性四个方面指出问题。

            原始提示词：
            {{originalPrompt}}

            请直接输出分析结果，语言简洁明确。
            """;

    private final ChatClient chatClient;

    @Qualifier("promptOptimizeOllamaChatOptions")
    private final OllamaChatOptions promptOptimizeOllamaChatOptions;

    public String analyze(String originalPrompt, PromptTemplate template) {
        String userPrompt = PromptTemplateRenderUtil.render(
                ANALYZE_PROMPT,
                Map.of("originalPrompt", defaultIfBlank(originalPrompt, "未提供原始提示词"))
        );

        String content = chatClient.prompt()
                .options(promptOptimizeOllamaChatOptions)
                .system(defaultIfBlank(template == null ? null : template.getSystemPrompt(), ""))
                .user(userPrompt)
                .call()
                .content();

        if (!StringUtils.hasText(content)) {
            throw new BusinessException("prompt analyze result is empty");
        }
        return content.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
