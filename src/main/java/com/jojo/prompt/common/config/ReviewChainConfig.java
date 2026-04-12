package com.jojo.prompt.common.config;

import com.jojo.prompt.common.handler.OriginalityReviewHandler;
import com.jojo.prompt.common.handler.PromptReviewHandler;
import com.jojo.prompt.common.handler.QualityReviewHandler;
import com.jojo.prompt.common.handler.SensitiveWordReviewHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReviewChainConfig {

    @Bean
    public PromptReviewHandler reviewChain(
            SensitiveWordReviewHandler sensitiveWordReviewHandler,
            QualityReviewHandler qualityReviewHandler,
            OriginalityReviewHandler originalityReviewHandler
    ) {
        //构造责任链
        sensitiveWordReviewHandler.setNext(qualityReviewHandler);
        qualityReviewHandler.setNext(originalityReviewHandler);

        return sensitiveWordReviewHandler;
    }
}
