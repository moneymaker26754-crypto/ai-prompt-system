package com.jojo.prompt.integration.ai;

import com.jojo.prompt.entity.PromptTemplate;

public interface PromptAiGateway {

    PromptAnalyzeResult analyze(
            String originalPrompt,
            PromptTemplate template
    );
}