package com.jojo.prompt.service.impl;

import com.jojo.prompt.common.mq.message.PromptCountSyncMessage;
import com.jojo.prompt.common.mq.producer.PromptMqProducer;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
//实现定时将缓存数据打入数据库
//用消息队列实现异步增量更新，减少对数据库的压力
public class PromptCountSyncTask {
    private static final int MAX_DISPATCH_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MS = 1_000L;

    private final RedisCacheService redisCacheService;
    private final PromptMqProducer promptMqProducer;
    //2.5更改，应对脏读问题
    @Scheduled(fixedDelayString = "${prompt.count.rescan-interval-ms:300000}")
    public void syncPromptCounts() {
        Set<Long> dirtyPromptIds = redisCacheService.getDirtyPromptId();
        if(dirtyPromptIds.isEmpty()) {
            return;
        }

        for (Long promptId : dirtyPromptIds) {
            dispatch(promptId);
        }
    }

    private void dispatch(Long promptId) {
//        for (int attempt = 1; attempt <= MAX_DISPATCH_ATTEMPTS; attempt++) {
//            try {
//                promptMqProducer.sendPromptCountSyncMessage(promptId);
//                return;
//            } catch (Exception ex) {
//                if (attempt == MAX_DISPATCH_ATTEMPTS) {
//                    log.error("re-dispatch prompt count sync message failed after retries, promptId={}", promptId, ex);
//                    return;
//                }
//                long backoffMs = BASE_RETRY_DELAY_MS * (1L << (attempt - 1));
//                log.warn("re-dispatch prompt count sync message failed, promptId={}, attempt={}, retryAfterMs={}",
//                        promptId, attempt, backoffMs, ex);
//                try {
//                    Thread.sleep(backoffMs);
//                } catch (InterruptedException interruptedException) {
//                    Thread.currentThread().interrupt();
//                    log.warn("re-dispatch prompt count sync message interrupted, promptId={}", promptId);
//                    return;
//                }
//            }
//        }
        boolean success = promptMqProducer.dispatchWithRetry(promptId);
        if(!success) {
            log.warn("dispatch prompt count sync message failed, promptId={}", promptId);
        }
    }
}
