package com.jojo.prompt.common.mq.consumer;

import com.jojo.prompt.common.mq.message.PromptCountSyncMessage;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.jojo.prompt.common.constant.PromptMqConstant.PROMPT_COUNT_SYNC_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptCountSyncConsumer {
    private final RedisCacheService redisCacheService;

    @RabbitListener(queues = PROMPT_COUNT_SYNC_QUEUE)
    public void consume(PromptCountSyncMessage message) {
        try {
            boolean success = redisCacheService.syncCountToDb(message.promptId());
            if(success) {
                redisCacheService.removeDirtyPromptId(Set.of(message.promptId()));
                log.info("prompt sync success, promptId={}", message.promptId());
                return;
            }
            log.debug("skip prompt sync because another worker is processing it, promptId={}", message.promptId());
        }catch(Exception ex) {
            log.error("prompt sync failed and will be retried, promptId={}", message.promptId(), ex);
            throw ex;
        }
    }
}
