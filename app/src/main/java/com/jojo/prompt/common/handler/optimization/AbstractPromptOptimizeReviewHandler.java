package com.jojo.prompt.common.handler.optimization;

public abstract class AbstractPromptOptimizeReviewHandler implements PromptOptimizeReviewHandler {

    protected PromptOptimizeReviewHandler next;

    @Override
    public PromptOptimizeReviewHandler setNext(PromptOptimizeReviewHandler next) {
        this.next = next;
        return next;
    }

    @Override
    public void review(PromptOptimizeReviewContext context) {
        doReview(context);
        if (next != null) {
            next.review(context);
        }
    }

    protected abstract void doReview(PromptOptimizeReviewContext context);

    protected abstract String getNodeName();
}
