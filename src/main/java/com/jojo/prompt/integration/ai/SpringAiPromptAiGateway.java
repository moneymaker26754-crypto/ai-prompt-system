package com.jojo.prompt.integration.ai;

import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.service.agent.PromptAnalyzeAgent;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.swing.*;

@Component
@ConditionalOnProperty(
        prefix = "app.ai",
        name = "provider",
        havingValue = "spring-ai",
        matchIfMissing = true
)
public class SpringAiPromptAiGateway implements PromptAiGateway {

    private final PromptAnalyzeAgent analyzeAgent;

    private final OllamaChatOptions options;

    public SpringAiPromptAiGateway(
            PromptAnalyzeAgent analyzeAgent,
            @Qualifier("promptOptimizeOllamaChatOptions")
            OllamaChatOptions options
    ) {
        this.analyzeAgent = analyzeAgent;
        this.options = options;
    }
    @Override
    public PromptAnalyzeResult analyze(String originalPrompt, PromptTemplate template) {

        String analysis = analyzeAgent.analyze(originalPrompt, template);

        return new PromptAnalyzeResult(analysis, options.getModel());
    }
}
