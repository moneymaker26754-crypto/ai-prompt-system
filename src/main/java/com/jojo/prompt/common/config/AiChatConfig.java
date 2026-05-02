package com.jojo.prompt.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiChatConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean("promptOptimizeOllamaChatOptions")
    public OllamaChatOptions promptOptimizeOllamaChatOptions(
            @Value("${spring.ai.ollama.chat.options.model:qwen3.5:9b}") String modelName) {
        return OllamaChatOptions.builder()
                .model(modelName)
                .disableThinking()
                .build();
    }
}
