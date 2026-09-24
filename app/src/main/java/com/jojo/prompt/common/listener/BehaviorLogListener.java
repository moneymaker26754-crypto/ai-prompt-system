package com.jojo.prompt.common.listener;

import com.jojo.prompt.common.event.PromptCreateEvent;
import com.jojo.prompt.common.event.PromptFavoriteEvent;
import com.jojo.prompt.common.event.PromptLikeEvent;
import com.jojo.prompt.common.mq.MiniMqEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 事件监听器3：记录行为日志。
 *
 * <p>渐进替换：prompt.mq.mode=minimq 时事件经自研 mini-MQ 解耦投递
 * （MiniMqEventConsumer 消费落日志），broker 不可用自动回退本地记录。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorLogListener {

    private final ObjectProvider<MiniMqEventPublisher> miniMqPublisher;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromptLiked(PromptLikeEvent event) {
        if (publishViaMiniMq(event.getUserId(), "like", event.getPromptId(), event.getLikeTime())) {
            return;
        }
        log.info("[behaviorLogListener] receive like event : userId={}, promptId={},",
                event.getUserId(), event.getPromptId());
        //记录到行为分析系统
        String action = "like";
        try {
            logBehavior(event.getUserId(), action, event.getPromptId(), event.getLikeTime());
            log.debug("[behaviorLogListener] record like behavior success");
        } catch (Exception e) {
            log.error("[behaviorLogListener] record like behavior failed", e);
        }
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromptFavorite(PromptFavoriteEvent event) {
        if (publishViaMiniMq(event.getUserId(), "favorite", event.getPromptId(), event.getFavoriteTime())) {
            return;
        }
        log.info("[behaviorLogListener] receive favorite event : userId={}, promptId={},",
                event.getUserId(), event.getPromptId());
        //记录到行为分析系统
        String action = "favorite";
        try {
            logBehavior(event.getUserId(), action, event.getPromptId(), event.getFavoriteTime());
            log.debug("[behaviorLogListener] record favorite behavior success");
        } catch (Exception e) {
            log.error("[behaviorLogListener] record favorite behavior failed", e);
        }
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromptCreated(PromptCreateEvent event) {
        if (publishViaMiniMq(event.getUserId(), "create", event.getPromptId(), event.getCreateTime())) {
            return;
        }
        log.info("[behaviorLogListener] receive create event : userId={}, promptId={},",
                event.getUserId(), event.getPromptId());
        //记录到行为分析系统
        String action = "create";
        try {
            logBehavior(event.getUserId(), action, event.getPromptId(), event.getCreateTime());
            log.debug("[behaviorLogListener] record create behavior success");
        } catch (Exception e) {
            log.error("[behaviorLogListener] record create behavior failed", e);
        }
    }

    /** @return true=已投递 mini-MQ；false=应回退本地处理 */
    private boolean publishViaMiniMq(Long userId, String action, Long promptId, LocalDateTime time) {
        if (miniMqPublisher == null) {
            return false;
        }
        MiniMqEventPublisher publisher = miniMqPublisher.getIfAvailable();
        return publisher != null && publisher.publishBehavior(userId, action, promptId, time);
    }

    private void logBehavior(Long userId, String action, Long relatedId, LocalDateTime timeStamp) {
        //实际实现，保存到user_behavior_log表，或者发送到Kafka/RabbitMQ等消息队列供行为分析系统消费
        log.info("log behavior -> userId={}, action={}, relatedId={}, time={}",
                userId, action, relatedId, timeStamp);
    }


}
