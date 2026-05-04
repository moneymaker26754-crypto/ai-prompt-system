package com.jojo.prompt.service.agent;


import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.utils.PromptTemplateRenderUtil;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.entity.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Map;

//第二版新增流式Agent
@Service
@RequiredArgsConstructor
public class PromptOptimizeStreamAgent {

    private static final String OPTIMIZE_PROMPT = """
            请基于以下信息优化提示词，并且只返回优化后的提示词内容，不要附加解释、标题或 Markdown。

            优化指令：
            {{instruction}}

            优化目标：
            {{target}}

            期望输出格式：
            {{outputFormat}}

            原始提示词：
            {{originalPrompt}}

            分析结果：
            {{analysisResult}}
            """;

    public final ChatClient chatClient;

    @Qualifier("promptOptimizeOllamaChatOptions")
    private final OllamaChatOptions promptOptimizeOllamaChatOptions;

    public Flux<String> optimizeStream(PromptOptimizeRequestDTO dto,
                                       PromptTemplate template,
                                       String analysisResult) {
        String userPrompt = PromptTemplateRenderUtil.render(
                OPTIMIZE_PROMPT,
                Map.of(
                        "instruction", defaultIfBlank(template == null ? null : template.getOptimizeInstruction(),
                                "请在不改变原始意图的前提下，提升提示词的清晰度、完整性和可执行性。"),
                        "target", defaultIfBlank(dto == null ? null : dto.getTarget(),
                                "未指定，请优先提升表达清晰度和执行可操作性。"),
                        "outputFormat", defaultIfBlank(dto == null ? null : dto.getOutputFormat(),
                                "未指定，请采用最适合任务目标的输出形式。"),
                        "originalPrompt", defaultIfBlank(dto == null ? null : dto.getOriginalPrompt(),
                                "未提供原始提示词"),
                        "analysisResult", defaultIfBlank(analysisResult, "无额外分析结果")
                )
        );

        if (!StringUtils.hasText(userPrompt)) {
            throw new BusinessException("prompt optimize request is empty");
        }

        return chatClient.prompt()
                .options(promptOptimizeOllamaChatOptions)
                .system(defaultIfBlank(template == null ? null : template.getSystemPrompt(), ""))
                .user(userPrompt)
                .stream()
                .content();
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
