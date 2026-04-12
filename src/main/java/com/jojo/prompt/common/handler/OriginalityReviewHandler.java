package com.jojo.prompt.common.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OriginalityReviewHandler implements PromptReviewHandler {

    private PromptReviewHandler next;

    @Override
    public void setNext(PromptReviewHandler next) {
        this.next = next;
    }

    @Override
    public void review(com.jojo.prompt.entity.Prompt prompt) {
        //检测是否抄袭
        double similarity = checkSimilarity(prompt.getContent());
        //原创性检测
        if (similarity > 0.8) {
            throw new RuntimeException("content suspected of plagiarism");
        }
        log.info("originality check passed");
        //传递给下一个处理器
        if (next != null) {
            next.review(prompt);
        }
    }

    private double checkSimilarity(String content) {
        //Todo: 使用向量相似度检测
        return 0.3;
    }
}
