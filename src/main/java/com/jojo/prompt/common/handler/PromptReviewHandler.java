package com.jojo.prompt.common.handler;

import com.jojo.prompt.entity.Prompt;

//审核处理器接口
public interface PromptReviewHandler {
    PromptReviewHandler setNext(PromptReviewHandler next);
    void review(Prompt prompt);
}
//敏感词检测、质量检测、原创性检测、审核链配置
