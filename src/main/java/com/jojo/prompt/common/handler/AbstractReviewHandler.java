package com.jojo.prompt.common.handler;

import com.jojo.prompt.entity.Prompt;
import lombok.extern.slf4j.Slf4j;

//抽象审核处理器
@Slf4j
public abstract class AbstractReviewHandler implements PromptReviewHandler{

    protected PromptReviewHandler next;

    @Override
    public PromptReviewHandler setNext(PromptReviewHandler next) {
        this.next = next;
        return next;
    }

    @Override
    public void review(Prompt prompt) {
        //执行当前处理器的审核逻辑
        doReview(prompt);
        //通过审核传给下一个处理器
        if(next != null) {
            next.review(prompt);
        }
    }

    //具体审核逻辑，由子类实现
    protected abstract void doReview(Prompt prompt);

    protected abstract String getHandlerName();
}
