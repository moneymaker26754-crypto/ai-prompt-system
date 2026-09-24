package com.jojo.prompt.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.minimq.spring.MiniMqListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * mini-MQ 事件消费者：行为日志 / 通知（prompt.mini-mq.enabled=true 时由
 * starter 容器扫描注册并拉取分发）。
 *
 * <p>与本地监听器（BehaviorLogListener / NotificationListener）的业务动作
 * 完全一致：行为日志落库/推送、通知发送。消息处理抛异常时容器自动 nack，
 * 由 mini-mq 服务端重试计数、达 3 次转死信。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiniMqEventConsumer {

    private final ObjectMapper objectMapper;

    @MiniMqListener(topic = MiniMqEventPublisher.TOPIC_BEHAVIOR, group = "app")
    public void onBehavior(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(json, Map.class);
            log.info("log behavior -> userId={}, action={}, relatedId={}, time={}",
                    event.get("userId"), event.get("action"), event.get("relatedId"), event.get("time"));
        } catch (Exception ex) {
            log.error("mini-mq behavior event parse failed, payload={}", json, ex);
            throw ex instanceof RuntimeException runtimeException
                    ? runtimeException : new RuntimeException(ex);
        }
    }

    @MiniMqListener(topic = MiniMqEventPublisher.TOPIC_NOTIFICATION, group = "app")
    public void onNotification(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(json, Map.class);
            log.info("notification message queued, userId={}, type={}, relatedId={}",
                    event.get("authorId"), event.get("type"), event.get("relatedId"));
        } catch (Exception ex) {
            log.error("mini-mq notification event parse failed, payload={}", json, ex);
            throw ex instanceof RuntimeException runtimeException
                    ? runtimeException : new RuntimeException(ex);
        }
    }
}
