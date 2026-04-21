package com.jojo.prompt.common.mq.producer;

import com.jojo.prompt.common.mq.message.PromptCountSyncMessage;
import com.jojo.prompt.common.mq.message.PromptReviewMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.jojo.prompt.common.constant.PromptMqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptMqProducer {

    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    //重试参数
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_RETRY_MS = 500L;
    private static final long BASE_RETRY_DELAY_MS = 1_000L;

    @Value("${prompt.count.sync-delay-ms:60000}")
    private long countSyncDelayMs;

    public void sendPromptReviewMessage(PromptReviewMessage message) {
        rabbitTemplate.convertAndSend(
                PROMPT_REVIEW_EXCHANGE,
                PROMPT_REVIEW_ROUTING_KEY,
                message
        );
    }

    public void sendPromptCountSyncMessage(Long promptId) {
        String dispatchKey = PROMPT_COUNT_SYNC_DISPATCH_KEY + promptId;
        Boolean firstDispatch = stringRedisTemplate
                .opsForValue().setIfAbsent(dispatchKey, "1", countSyncDelayMs + 10_000, TimeUnit.MILLISECONDS);
        if (!Boolean.TRUE.equals(firstDispatch)) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    PROMPT_COUNT_DELAY_EXCHANGE,
                    PROMPT_COUNT_DELAY_ROUTING_KEY,
                    new PromptCountSyncMessage(promptId),
                    msg -> {
                        msg.getMessageProperties().setExpiration(String.valueOf(countSyncDelayMs));
                        return msg;
                    }
            );
        } catch (Exception ex) {
            // 本次发送没成功，释放 dispatchKey，允许后续重试重新投递
            stringRedisTemplate.delete(dispatchKey);
            throw ex;
        }
    }

    public boolean sendPromptReviewMessageWithRetry(PromptReviewMessage message) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                sendPromptReviewMessage(message);
                return true;

            } catch (Exception ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("send prompt review message failed after retries, promptId={}, expectedVersion={}, action={}",
                            message.promptId(), message.expectedVersion(), message.operationType(), ex);

                    return false;
                }

                long backoffMs = BASE_RETRY_MS * (1L << (attempt - 1));
                log.warn("send prompt review message failed, promptId={}, expectedVersion={}, action={}, attempt={}, retryAfterMs={}",
                        message.promptId(), message.expectedVersion(), message.operationType(), attempt, backoffMs, ex);

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    log.warn("send prompt review message interrupted, promptId={}, expectedVersion={}, action={}",
                            message.promptId(), message.expectedVersion(), message.operationType());

                    return false;
                }

            }
        }
        return false;
    }

    public boolean dispatchWithRetry(Long promptId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                sendPromptCountSyncMessage(promptId);
                return true;

            } catch (Exception ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("re-dispatch prompt count sync message failed after retries, promptId={}", promptId, ex);
                    return false;
                }
                long backoffMs = BASE_RETRY_DELAY_MS * (1L << (attempt - 1));
                log.warn("re-dispatch prompt count sync message failed, promptId={}, attempt={}, retryAfterMs={}",
                        promptId, attempt, backoffMs, ex);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    log.warn("re-dispatch prompt count sync message interrupted, promptId={}", promptId);
                    return false;
                }
            }
        }
        return false;
    }
}
