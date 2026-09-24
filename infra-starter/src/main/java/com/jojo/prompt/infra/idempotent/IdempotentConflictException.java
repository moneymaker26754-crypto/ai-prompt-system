package com.jojo.prompt.infra.idempotent;

/** 命中幂等占位（重复请求）时抛出；由业务方全局异常处理器决定 HTTP 状态码。 */
public class IdempotentConflictException extends RuntimeException {

    public IdempotentConflictException(String message) {
        super(message);
    }
}
