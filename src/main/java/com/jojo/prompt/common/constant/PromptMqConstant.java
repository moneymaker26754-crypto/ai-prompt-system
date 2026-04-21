package com.jojo.prompt.common.constant;

public interface PromptMqConstant {

    //审核相关队列
    String PROMPT_REVIEW_EXCHANGE = "prompt.review.exchange";
    String PROMPT_REVIEW_QUEUE = "prompt.review.queue";
    String PROMPT_REVIEW_ROUTING_KEY = "prompt.review";
    //死信相关
    String PROMPT_REVIEW_DLX_EXCHANGE = "prompt.review.dlx.exchange";
    String PROMPT_REVIEW_DLX_QUEUE = "prompt.review.dlx.queue";
    String PROMPT_REVIEW_DLX_ROUTING_KEY = "prompt.review.dlx";

    //延迟消息队列
    String PROMPT_COUNT_DELAY_EXCHANGE = "prompt.count.delay.exchange";
    String PROMPT_COUNT_DELAY_QUEUE = "prompt.count.delay.queue";
    String PROMPT_COUNT_DELAY_ROUTING_KEY = "prompt.count.delay";

    //计数异步操作队列
    String PROMPT_COUNT_SYNC_EXCHANGE = "prompt.count.sync.exchange";
    String PROMPT_COUNT_SYNC_QUEUE = "prompt.count.sync.queue";
    String PROMPT_COUNT_SYNC_ROUTING_KEY = "prompt.count.sync";
    //死信相关
    String PROMPT_COUNT_SYNC_DLX_EXCHANGE = "prompt.count.sync.dlx.exchange";
    String PROMPT_COUNT_SYNC_DLX_QUEUE = "prompt.count.sync.dlx.queue";
    String PROMPT_COUNT_SYNC_DLX_ROUTING_KEY = "prompt.count.sync.dlx";

    String PROMPT_COUNT_SYNC_DISPATCH_KEY = "prompt:count:sync:dispatch:";
}
