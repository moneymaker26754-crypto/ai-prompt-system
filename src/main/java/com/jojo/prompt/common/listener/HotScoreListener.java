package com.jojo.prompt.common.listener;

import com.jojo.prompt.entity.PromptLikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

//事件监听器1：更新热度分数
@Slf4j
@Component
public class HotScoreListener {
    @EventListener
    @Async
    public void onPromptLiked(PromptLikeEvent event) {
        log.info("update hot score: promptId={},",  event.getPromptId());
        //todo:更新redis中热度分数
    }
}
