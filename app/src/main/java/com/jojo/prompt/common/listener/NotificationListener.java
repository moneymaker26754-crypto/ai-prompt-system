package com.jojo.prompt.common.listener;

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

/**
 * 事件监听器2：发送通知。
 *
 * <p>渐进替换：prompt.mq.mode=minimq 时经自研 mini-MQ 解耦投递，
 * broker 不可用自动回退本地发送。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final ObjectProvider<MiniMqEventPublisher> miniMqPublisher;

    //监听喜欢事件 - 发送通知
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromptLiked(PromptLikeEvent event) {
        //发送通知消息给prompt作者
        String type = "like";
        try {
            if (event.getUserId().equals(event.getAuthorId())) {
                return;
            }
            if (publishViaMiniMq(event.getAuthorId(),
                    String.format("user %d like your prompt", event.getUserId()),
                    type, event.getPromptId())) {
                return;
            }
            sendNotification(
                    event.getAuthorId(),
                    String.format("user %d like your prompt", event.getUserId()),
                    type,
                    event.getPromptId());

            log.info("notification sent, eventType=like, authorId={}, promptId={}, status=success", event.getAuthorId(), event.getPromptId());

        } catch (Exception e) {
            log.error("notification send failed, eventType=like, authorId={}, promptId={}, status=failed", event.getAuthorId(), event.getPromptId(), e);
        }
    }

    //监听收藏事件 - 发送通知
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromptFavorite(PromptFavoriteEvent event) {
        String type = "favorite";
        try {
            if (event.getUserId().equals(event.getAuthorId())) {
                return;
            }
            if (publishViaMiniMq(event.getAuthorId(),
                    String.format("user %d favorite your prompt", event.getUserId()),
                    type, event.getPromptId())) {
                return;
            }
            sendNotification(
                    event.getAuthorId(),
                    String.format("user %d favorite your prompt", event.getUserId()),
                    type,
                    event.getPromptId());
            log.info("notification sent, eventType=favorite, authorId={}, promptId={}, status=success", event.getAuthorId(), event.getPromptId());
        } catch (Exception e) {
            log.error("notification send failed, eventType=favorite, authorId={}, promptId={}, status=failed", event.getAuthorId(), event.getPromptId(), e);
        }
    }

    /** @return true=已投递 mini-MQ；false=应回退本地处理 */
    private boolean publishViaMiniMq(Long authorId, String message, String type, Long promptId) {
        if (miniMqPublisher == null) {
            return false;
        }
        MiniMqEventPublisher publisher = miniMqPublisher.getIfAvailable();
        return publisher != null && publisher.publishNotification(authorId, message, type, promptId);
    }

    //模拟发送通知
    private void sendNotification(Long userId, String message, String type, Long relatedId) {
        //现实实现，保存到通知表。通过WebSocket推送给用户，或者通过第三方服务发送邮件或短信等
        log.info("notification message queued, userId={}, type={}, relatedId={}", userId, type, relatedId);
    }
}
