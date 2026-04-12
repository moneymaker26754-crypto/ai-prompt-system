package com.jojo.prompt.common.listener;

import com.jojo.prompt.entity.PromptLikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

//事件监听器3：记录行为日志
@Slf4j
@Component
public class BehaviorLogListener {
    @EventListener
    @Async
    public void onPromptLiked(PromptLikeEvent event) {
        log.info("record behavior log: promptId={},", event.getPromptId());
        //todo: 记录到行为分析系统
    }
}
