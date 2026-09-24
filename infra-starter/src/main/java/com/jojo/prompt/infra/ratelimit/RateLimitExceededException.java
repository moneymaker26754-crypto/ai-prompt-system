package com.jojo.prompt.infra.ratelimit;

/** 触发限流时抛出；由业务方全局异常处理器决定 HTTP 状态码。 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
