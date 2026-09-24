package com.jojo.prompt.minimq.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式 mini-mq 消费者：标注在 bean 方法上，容器自动拉取并分发。
 *
 * <pre>
 * &#64;MiniMqListener(topic = "behavior-log", group = "app")
 * public void onEvent(String payload) { ... }
 * </pre>
 *
 * <p>方法签名支持：{@code (String payload)} 或 {@code (byte[] payload)}；
 * 调用成功自动 ack，抛异常自动 nack（触发服务端重试计数/死信）。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiniMqListener {

    /** 消费的 topic */
    String topic();

    /** 消费组（组内游标独立） */
    String group() default "default";
}
