package com.jojo.prompt.service.impl;

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
public class PromptCountSyncTask {

    private final RedisCacheService redisCacheService;
    //2.5更改，应对脏读问题
    @Scheduled(fixedDelay = 300000)
    public void syncPromptCounts() {
        Set<Long> dirtyPromptIds = redisCacheService.getDirtyPromptId();
        if(dirtyPromptIds.isEmpty()) {
            return;
        }
        //用hashset，自主去重，顺序无关，还能快速查找
        Set<Long> syncedPromptIds = new HashSet<>();
        for(Long promptId : dirtyPromptIds) {
            try {
                boolean isSuccess = redisCacheService.syncCountToDb(promptId);
                if(isSuccess) {
                    syncedPromptIds.add(promptId);
                }
            } catch (Exception e) {
                log.error("sync prompt count failed, promptId={}", promptId, e);
            }
        }
        redisCacheService.removeDirtyPromptId(syncedPromptIds);
    }
}
