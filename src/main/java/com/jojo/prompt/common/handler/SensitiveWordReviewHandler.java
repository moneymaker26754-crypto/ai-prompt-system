package com.jojo.prompt.common.handler;

import com.jojo.prompt.entity.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

//敏感词检测
@Slf4j
@Component
public class SensitiveWordReviewHandler implements PromptReviewHandler {

    private PromptReviewHandler next;


    @Override
    public void setNext(PromptReviewHandler next) {
        this.next = next;
    }

    @Override
    public void review(Prompt prompt) {
        //敏感词检测
        if (constraintSensitiveWords(prompt.getContent())) {
            throw new RuntimeException("prompt contains sensitive words");
        }
        log.info("sensitive word check passed");
        //传递给下一个处理器
        if (next != null) {
            next.review(prompt);
        }
    }

    private boolean constraintSensitiveWords(String content) {
        //TODO: 实现敏感词检测逻辑，可以使用第三方库或自定义实现
        //示例：简单的敏感词列表
        String[] sensitiveWords = {"badword1", "badword2", "badword3" };
        for (String word : sensitiveWords) {
            if (content != null && content.toLowerCase().contains(word)) {
                return false;
            }
        }
        return true;
    }
}
