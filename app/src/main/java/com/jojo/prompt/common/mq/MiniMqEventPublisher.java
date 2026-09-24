package com.jojo.prompt.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.minimq.spring.MiniMqTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 行为日志 / 通知事件的 mini-MQ 投递器（prompt.mq.mode=minimq 时生效）。
 *
 * <p>渐进替换策略：默认 mode=local 走进程内 @Async 监听；切到 minimq 后
 * 事件经自研 mini-MQ 解耦投递，RabbitMQ 审核/计数主链路不受影响。</p>
 *
 * <p>降级策略：broker 不可用时 publish 返回 false，调用方回退本地处理，
 * 行为日志/通知不因中间件故障丢失。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "prompt.mq.mode", havingValue = "minimq")
public class MiniMqEventPublisher {

    public static final String TOPIC_BEHAVIOR = "behavior-log";
    public static final String TOPIC_NOTIFICATION = "notification";

    private final MiniMqTemplate miniMqTemplate;
    private final ObjectMapper objectMapper;

    /** @return false 表示投递失败，调用方应回退本地处理 */
    public boolean publishBehavior(Long userId, String action, Long relatedId, LocalDateTime time) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("action", action);
        payload.put("relatedId", relatedId);
        payload.put("time", String.valueOf(time));
        return tryPublish(TOPIC_BEHAVIOR, payload);
    }

    /** @return false 表示投递失败，调用方应回退本地处理 */
    public boolean publishNotification(Long authorId, String message, String type, Long relatedId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authorId", authorId);
        payload.put("message", message);
        payload.put("type", type);
        payload.put("relatedId", relatedId);
        return tryPublish(TOPIC_NOTIFICATION, payload);
    }

    private boolean tryPublish(String topic, Map<String, Object> payload) {
        try {
            miniMqTemplate.publish(topic, objectMapper.writeValueAsString(payload));
            return true;
        } catch (Exception ex) {
            log.warn("mini-mq publish failed, topic={}, will fallback to local", topic, ex);
            return false;
        }
    }
}
