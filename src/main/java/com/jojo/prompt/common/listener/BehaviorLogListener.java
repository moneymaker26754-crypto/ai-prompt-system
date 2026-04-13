package com.jojo.prompt.common.listener;

import com.jojo.prompt.common.event.PromptLikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

//事件监听器3：记录行为日志
@Slf4j
@Component
public class BehaviorLogListener {
    @EventListener
    @Async("eventExecutor")
    public void onPromptLiked(PromptLikeEvent event) {
        log.info("[behaviorLogListener] receive like event : userId={}, promptId={},",
                event.getUserId(), event.getPromptId());
        //记录到行为分析系统
        try {
            logBehavior(
                    event.getUserId(),
                    "like",
                    event.getPromptId(),
                    event.getLikeTime()
            );

            log.debug("[behaviorLogListener] record like behavior success");

        } catch (Exception e) {
            log.error("[behaviorLogListener] record like behavior failed", e);
        }
    }

    @EventListener
    @Async("eventExecutor")
    public void onPromptFavorite(PromptLikeEvent event) {
        log.info("[behaviorLogListener] receive favorite event : userId={}, promptId={},",
                event.getUserId(), event.getPromptId());
        //记录到行为分析系统
        try {
            logBehavior(
                    event.getUserId(),
                    "favorite",
                    event.getPromptId(),
                    event.getLikeTime()
            );

            log.debug("[behaviorLogListener] record favorite behavior success");

        } catch (Exception e) {
            log.error("[behaviorLogListener] record favorite behavior failed", e);
        }
    }

    @EventListener
    @Async("eventExecutor")
    public void onPromptCreated(PromptLikeEvent event) {
        log.info("[behaviorLogListener] receive create event : userId={}, promptId={},",
                event.getUserId(), event.getPromptId());
        //记录到行为分析系统
        try {
            logBehavior(
                    event.getUserId(),
                    "create",
                    event.getPromptId(),
                    event.getLikeTime()
            );

            log.debug("[behaviorLogListener] record create behavior success");

        } catch (Exception e) {
            log.error("[behaviorLogListener] record create behavior failed", e);
        }
    }

    private void logBehavior(Long userId, String action, Long relatedId, LocalDateTime timeStamp) {
        //实际实现，保存到user_behavior_log表，或者发送到Kafka/RabbitMQ等消息队列供行为分析系统消费
        log.info("log behavior -> userId={}, action={}, relatedId={}, time={}",
                userId, action, relatedId, timeStamp);
    }


}
