package com.jojo.prompt.common.listener;

import com.jojo.prompt.common.event.PromptLikeEvent;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static com.jojo.prompt.common.constant.RedisKeyConstant.PROMPT_HOT_FAVORITE;
import static com.jojo.prompt.common.constant.RedisKeyConstant.PROMPT_HOT_LIKE;

//事件监听器1：更新热度分数
@Slf4j
@Component
@RequiredArgsConstructor
public class HotScoreListener {

    private final RedisCacheService redisCacheService;


    //监听点赞事件
    @EventListener
    @Async("eventExecutor")
    public void onPromptLiked(PromptLikeEvent event) {
        log.info("[hotScoreListener] get like event: promptId={}",  event.getPromptId());
        try{
            //更新redis中热度的分数
            String key = PROMPT_HOT_LIKE;
            redisCacheService.updateHotRanking(event.getPromptId(), key, 5.0);

            log.info("[hotScoreListener] hot score update success, promptId={}", event.getPromptId());
        }catch (Exception e) {
            log.error("[hotScoreListener] hot score update failed, promptId={}", event.getPromptId(), e);
        }
    }
    //监听收藏事件
    @EventListener
    @Async("eventExecutor")
    public void onPromptFavorite(PromptLikeEvent event) {
        log.info("[hotScoreListener] get favorite event: promptId={}",  event.getPromptId());
        try{
            //更新redis中热度的分数
            String key = PROMPT_HOT_FAVORITE;
            redisCacheService.updateHotRanking(event.getPromptId(), key, 10.0);

            log.info("[hotScoreListener] hot score update success, promptId={}", event.getPromptId());
        }catch (Exception e) {
            log.error("[hotScoreListener] hot score update failed, promptId={}", event.getPromptId(), e);
        }
    }
    //监听复制事件
    //监听浏览事件
}
