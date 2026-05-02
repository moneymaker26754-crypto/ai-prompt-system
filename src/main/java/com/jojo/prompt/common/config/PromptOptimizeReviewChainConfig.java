package com.jojo.prompt.common.config;

import com.jojo.prompt.common.handler.optimization.OptimizeBasicReviewHandler;
import com.jojo.prompt.common.handler.optimization.OptimizeSensitiveWordReviewHandler;
import com.jojo.prompt.common.handler.optimization.OptimizeStructureReviewHandler;
import com.jojo.prompt.common.handler.optimization.PromptOptimizeReviewHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PromptOptimizeReviewChainConfig {

    @Bean
    public PromptOptimizeReviewHandler promptOptimizeReviewChain(
            OptimizeBasicReviewHandler basicReviewHandler,
            OptimizeSensitiveWordReviewHandler sensitiveWordReviewHandler,
            OptimizeStructureReviewHandler structureReviewHandler) {
        basicReviewHandler
                .setNext(sensitiveWordReviewHandler)
                .setNext(structureReviewHandler);
        return basicReviewHandler;
    }
}
