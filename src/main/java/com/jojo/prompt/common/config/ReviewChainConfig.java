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
            //构造审核责任链从敏感词检测开始最后是原创性检测
            SensitiveWordReviewHandler sensitiveWordReviewHandler,
            QualityReviewHandler qualityReviewHandler,
            OriginalityReviewHandler originalityReviewHandler
    ) {
        //构造责任链
        sensitiveWordReviewHandler
                .setNext(qualityReviewHandler)
                .setNext(originalityReviewHandler);

        //返回链头
        return sensitiveWordReviewHandler;
    }

}
