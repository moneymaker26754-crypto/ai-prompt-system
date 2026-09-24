package com.jojo.prompt.infra.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式幂等：同一 key 在 TTL 内只允许成功执行一次。
 *
 * <p>语义：请求进来先 SETNX 占位 → 执行 → 业务抛异常则释放占位（允许重试）；
 * 成功则保留占位直到 TTL，重复请求直接拒绝。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 业务前缀（拼入 Redis key） */
    String key() default "";

    /** 幂等键的 SpEL 表达式（基于方法入参，如 #dto.recordId + ':' + #userId） */
    String expr() default "";

    /** 占位 TTL（秒）；0 = 使用全局默认 prompt.infra.idempotent.default-ttl-seconds */
    long ttlSeconds() default 0;

    /** 重复请求提示语 */
    String message() default "重复请求，请勿重复提交";
}
