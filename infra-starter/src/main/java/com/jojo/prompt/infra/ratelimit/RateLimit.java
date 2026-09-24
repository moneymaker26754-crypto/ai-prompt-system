package com.jojo.prompt.infra.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式滑动窗口限流。
 *
 * <p>在 Controller/Service 方法上标注即可；判定在 Redis 内由单条 Lua
 * 原子完成（ZSET 滑动窗口），多实例部署下仍共享同一窗口。</p>
 *
 * <pre>
 * &#64;RateLimit(limit = 20, windowSeconds = 60, dim = Dimension.IP)
 * public Result&lt;?&gt; search(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大请求数 */
    long limit();

    /** 滑动窗口长度（秒） */
    long windowSeconds() default 1;

    /** 业务前缀（拼入 Redis key，便于区分不同接口） */
    String key() default "";

    /** 限流维度 */
    Dimension dim() default Dimension.IP;

    /** dim = EXPR 时的 SpEL 表达式（基于方法入参求值） */
    String expr() default "";

    /** 触发限流时的提示语 */
    String message() default "请求过于频繁，请稍后再试";

    enum Dimension {
        /** 按来源 IP */
        IP,
        /** 按当前登录用户（无登录态时回退 IP） */
        USER,
        /** 按 SpEL 表达式结果（如订单号、手机号） */
        EXPR
    }
}
