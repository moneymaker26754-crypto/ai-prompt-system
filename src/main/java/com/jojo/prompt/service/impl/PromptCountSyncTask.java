package com.jojo.prompt.service.impl;

import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
//实现定时将缓存数据打入数据库
public class PromptCountSyncTask {

    private final PromptMapper promptMapper;
    private final RedisCacheService redisCacheService;

    @Scheduled(fixedDelay = 300000)
    public void syncPromptCounts() {
        promptMapper.selectList(null).forEach(prompt -> {
            try {
                redisCacheService.syncCountToDb(prompt.getId());
            }catch (Exception e){
                log.error("sync prompt count failed, promptId={}", prompt.getId(), e);
            }
        });
    }
}
