package com.jojo.prompt.service.agent;

import com.jojo.prompt.entity.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptAnalyzeAgent {

    private final ChatClient chatClient;

    @Qualifier("promptOptimizeOllamaChatOptions")
    private final OllamaChatOptions promptOptimizeOllamaChatOptions;

    public String analyze(String originalPrompt, PromptTemplate template) {
        return chatClient.prompt()
                .options(promptOptimizeOllamaChatOptions)
                .system(template.getSystemPrompt())
                .user(user -> user.text("""
                        Analyze the following prompt and identify issues in clarity,
                        constraints, output format, and context completeness.

                        Original prompt:
                        {prompt}
                        """).param("prompt", originalPrompt))
                .call()
                .content();
    }
}
