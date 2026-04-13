package com.jojo.prompt.common.listener;

import com.jojo.prompt.common.event.PromptFavoriteEvent;
import com.jojo.prompt.common.event.PromptLikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

//事件监听器：发送通知
@Slf4j
@Component
public class NotificationListener {
    //监听喜欢事件 - 发送通知
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromptLiked(PromptLikeEvent event) {
        log.info("[notificationListener] send like message: promptId={},", event.getPromptId());
        //发送通知消息给prompt作者
        try {
            if(event.getUserId().equals(event.getAuthorId())) {
                return;
            }
            sendNotification(
                    event.getAuthorId(),
                    String.format("user %d like your prompt", event.getUserId()),
                    "like",
                    event.getPromptId());

            log.info("[notificationListener] publish like message success: authorId={}", event.getAuthorId());

        }catch (Exception e) {
            log.error("[notificationListener] publish like message failed", e);
        }
    }
    //监听收藏事件 - 发送通知
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromptFavorite(PromptFavoriteEvent event) {
        log.info("[notificationListener] send favorite message: promptId={}", event.getPromptId());
        try {
            if(!event.getUserId().equals(event.getAuthorId())) {
                return;
            }
            sendNotification(
                    event.getAuthorId(),
                    String.format("user %d favorite your prompt", event.getUserId()),
                    "favorite",
                    event.getPromptId());
            log.info("[notificationListener] publish favorite message success: authorId={}", event.getAuthorId());
        }catch (Exception e) {
            log.error("[notificationListener] publish favorite message failed", e);
        }
    }


    //模拟发送通知
    private void sendNotification(Long userId, String message, String type, Long relatedId) {
        //现实实现，保存到通知表。通过WebSocket推送给用户，或者通过第三方服务发送邮件或短信等
        log.info("send message -> userId={}, message={}, type={}, relatedId={}", userId, message, type, relatedId);
    }
}
