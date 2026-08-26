package com.jojo.prompt.integration.ai;

import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.service.agent.PromptAnalyzeAgent;
import com.jojo.prompt.service.agent.PromptOptimizeAgent;
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

    private final PromptOptimizeAgent PromptOptimizeAgent;

    public SpringAiPromptAiGateway(
            PromptAnalyzeAgent analyzeAgent,
            @Qualifier("promptOptimizeOllamaChatOptions")
            OllamaChatOptions options,
            PromptOptimizeAgent PromptOptimizeAgent
    ) {
        this.analyzeAgent = analyzeAgent;
        this.options = options;
        this.PromptOptimizeAgent = PromptOptimizeAgent;
    }
    @Override
    public PromptAnalyzeResult analyze(String originalPrompt, PromptTemplate template) {

        String analysis = analyzeAgent.analyze(originalPrompt, template);

        return new PromptAnalyzeResult(analysis, options.getModel());
    }

    @Override
    public PromptOptimizeResult optimize(PromptOptimizeRequestDTO dto, PromptTemplate template, String analysisResult) {

        String optimizedPrompt = PromptOptimizeAgent.optimize(dto, template, analysisResult);

        return new PromptOptimizeResult(
                optimizedPrompt,
                options.getModel()
        );
    }
}
