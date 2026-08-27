package com.jojo.prompt.integration.ai;

import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.PromptOptimizeReviewResult;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.service.agent.PromptAnalyzeAgent;
import com.jojo.prompt.service.agent.PromptOptimizeAgent;
import com.jojo.prompt.service.agent.PromptReviewAgent;
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

    private final PromptAnalyzeAgent promptAnalyzeAgent;

    private final OllamaChatOptions options;

    private final PromptOptimizeAgent promptOptimizeAgent;

    private final PromptReviewAgent promptReviewAgent;

    public SpringAiPromptAiGateway(
            PromptAnalyzeAgent promptAnalyzeAgent,
            @Qualifier("promptOptimizeOllamaChatOptions")
            OllamaChatOptions options,
            PromptOptimizeAgent promptOptimizeAgent,
            PromptReviewAgent promptReviewAgent
    ) {
        this.promptAnalyzeAgent = promptAnalyzeAgent;
        this.options = options;
        this.promptOptimizeAgent = promptOptimizeAgent;
        this.promptReviewAgent = promptReviewAgent;
    }
    @Override
    public PromptAnalyzeResult analyze(String originalPrompt, PromptTemplate template) {

        String analysis = promptAnalyzeAgent.analyze(originalPrompt, template);

        return new PromptAnalyzeResult(analysis, options.getModel());
    }

    @Override
    public PromptOptimizeResult optimize(PromptOptimizeRequestDTO dto, PromptTemplate template, String analysisResult) {

        String optimizedPrompt = promptOptimizeAgent.optimize(dto, template, analysisResult);

        return new PromptOptimizeResult(
                optimizedPrompt,
                options.getModel()
        );
    }

    @Override
    public PromptReviewResult review(String originalPrompt, String optimizedPrompt) {
        PromptOptimizeReviewResult result = promptReviewAgent.review(originalPrompt, optimizedPrompt);

        return new PromptReviewResult(
                result.getScore(),
                result.getRiskLevel(),
                result.getChangedIntent(),
                result.getReviewComment(),
                options.getModel()
        );
    }
}
