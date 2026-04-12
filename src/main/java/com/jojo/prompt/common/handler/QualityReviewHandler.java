package com.jojo.prompt.common.handler;

import com.jojo.prompt.entity.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QualityReviewHandler implements PromptReviewHandler {

    private PromptReviewHandler next;

    @Override
    public void setNext(PromptReviewHandler next) {
        this.next = next;
    }

    @Override
    public void review(Prompt prompt) {
        //质量检测
        if (prompt.getContent().length() < 10) {
            throw new RuntimeException("prompt content is too short");
        }
        log.info("quality check passed");
        //传递给下一个处理器
        if (next != null) {
            next.review(prompt);
        }
    }
}
