package com.jojo.prompt.service.agent;

import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.entity.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptOptimizeAgent {

    private final ChatClient chatClient;

    @Qualifier("promptOptimizeOllamaChatOptions")
    private final OllamaChatOptions promptOptimizeOllamaChatOptions;

    public String optimize(PromptOptimizeRequestDTO dto, PromptTemplate template, String analysisResult) {
        return chatClient.prompt()
                .options(promptOptimizeOllamaChatOptions)
                .system(template.getSystemPrompt())
                .user(user -> user.text("""
                        Optimize the user's original prompt based on the template instruction
                        and the analysis result.

                        Optimization instruction:
                        {instruction}

                        User target:
                        {target}

                        Expected output format:
                        {outputFormat}

                        Original prompt:
                        {originalPrompt}

                        Analysis result:
                        {analysisResult}

                        Return only the optimized prompt content.
                        """)
                        .param("instruction", template.getOptimizeInstruction())
                        .param("target", dto.getTarget())
                        .param("outputFormat", dto.getOutputFormat())
                        .param("originalPrompt", dto.getOriginalPrompt())
                        .param("analysisResult", analysisResult))
                .call()
                .content();
    }
}
