package com.jojo.prompt.common.listener;

import com.jojo.prompt.entity.PromptLikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

//事件监听器：发送通知
@Slf4j
@Component
public class NotificationListener {
    @EventListener
    @Async
    public void onPromptLiked(PromptLikeEvent event) {
        log.info("send like message: promptId={},", event.getPromptId());
        //todo:发送通知消息给prompt作者
    }
}
