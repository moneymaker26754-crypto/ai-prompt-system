package com.jojo.prompt.common.handler.optimization;

public interface PromptOptimizeReviewHandler {

    PromptOptimizeReviewHandler setNext(PromptOptimizeReviewHandler next);

    void review(PromptOptimizeReviewContext context);
}
