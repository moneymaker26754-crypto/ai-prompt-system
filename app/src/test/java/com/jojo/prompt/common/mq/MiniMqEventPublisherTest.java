package com.jojo.prompt.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.minimq.spring.MiniMqTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * MiniMqEventPublisher 单测：JSON 载荷格式与 broker 故障降级语义。
 */
@ExtendWith(MockitoExtension.class)
class MiniMqEventPublisherTest {

    @Mock
    private MiniMqTemplate template;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MiniMqEventPublisher publisher() {
        return new MiniMqEventPublisher(template, objectMapper);
    }

    @Test
    void publishBehaviorSerializesToBehaviorTopic() {
        boolean ok = publisher().publishBehavior(10L, "like", 100L, LocalDateTime.of(2026, 1, 1, 0, 0));
        assertTrue(ok);
        verify(template).publish(eq(MiniMqEventPublisher.TOPIC_BEHAVIOR), startsWith("{\"userId\":10"));
    }

    @Test
    void publishNotificationSerializesToNotificationTopic() {
        boolean ok = publisher().publishNotification(20L, "user 10 like your prompt", "like", 100L);
        assertTrue(ok);
        verify(template).publish(eq(MiniMqEventPublisher.TOPIC_NOTIFICATION), startsWith("{\"authorId\":20"));
    }

    @Test
    void brokerDownFallsBackToLocal() {
        doThrow(new IllegalStateException("broker down"))
                .when(template).publish(anyString(), anyString());
        boolean ok = publisher().publishBehavior(10L, "like", 100L, LocalDateTime.now());
        assertFalse(ok, "broker 不可用时应返回 false 触发调用方本地回退");
    }
}
