package com.jojo.prompt.common.listener;

import com.jojo.prompt.common.event.PromptHeatEvent;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

//事件监听器1：更新热度分数
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptHeatListener {

    private final RedisCacheService redisCacheService;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    //靠hot字段统一监听热门总榜
    public void onPromptHeatListener(PromptHeatEvent  promptHeatEvent) {
        String type = "hot";
        double delta = switch(promptHeatEvent.getAction()) {
            case "view" -> 1.0;
            case "copy" -> 3.0;
            case "like" -> 5.0;
            case "unlike" -> -5.0;
            case "favorite" -> 8.0;
            case "unfavorite" -> -8.0;
            default -> 0.0;
        };

        if(delta == 0.0) {
            return;
        }
        redisCacheService.updateHotRanking(promptHeatEvent.getPromptId(), type, delta);
    }


}
