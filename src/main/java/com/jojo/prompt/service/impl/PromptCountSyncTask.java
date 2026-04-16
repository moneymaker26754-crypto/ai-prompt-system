package com.jojo.prompt.service.impl;

import com.jojo.prompt.common.mq.message.PromptReviewMessage;
import com.jojo.prompt.common.mq.producer.PromptMqProducer;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
//实现定时将缓存数据打入数据库
//用消息队列实现异步增量更新，减少对数据库的压力
public class PromptCountSyncTask {
    private final RedisCacheService redisCacheService;
    private final PromptMqProducer  promptMqProducer;
    //2.5更改，应对脏读问题
    @Scheduled(fixedDelayString = "${prompt.count.rescan-interval-ms:300000}")
    public void syncPromptCounts() {
        Set<Long> dirtyPromptIds = redisCacheService.getDirtyPromptId();
        if(dirtyPromptIds.isEmpty()) {
            return;
        }

        for(Long promptId : dirtyPromptIds) {
            try {
                promptMqProducer.sendPromptCountSyncMessage(promptId);
            } catch (Exception e) {
                log.error("re-dispatch prompt count sync message failed, promptId={}", promptId, e);
            }
        }
    }
}
