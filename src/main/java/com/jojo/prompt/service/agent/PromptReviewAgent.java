package com.jojo.prompt.service.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptReviewAgent {

    private final ChatClient chatClient;

    @Qualifier("promptOptimizeOllamaChatOptions")
    private final OllamaChatOptions promptOptimizeOllamaChatOptions;

    public String review(String originalPrompt, String optimizedPrompt) {
        return chatClient.prompt()
                .options(promptOptimizeOllamaChatOptions)
                .user(user -> user.text("""
                        请审核优化后的提示词是否保持原意，并给出评分。

                        原始提示词：
                        {originalPrompt}

                        优化后提示词
                        {optimizedPrompt}

                        请按以下格式返回：
                        评分：0-100
                        风险等级：LOW/MEDIUM/HIGH
                        审核意见：
                        """)
                        .param("originalPrompt", originalPrompt)
                        .param("optimizedPrompt", optimizedPrompt))
                .call()
                .content();
    }
}
