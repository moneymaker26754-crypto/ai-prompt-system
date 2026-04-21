package com.jojo.prompt.common.mq.consumer;

import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.handler.PromptReviewHandler;
import com.jojo.prompt.common.mq.message.PromptReviewMessage;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static com.jojo.prompt.common.constant.PromptMqConstant.PROMPT_REVIEW_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptReviewConsumer {

    private final PromptMapper promptMapper;
    private final PromptReviewHandler reviewChain;
    private final RedisCacheService redisCacheService;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = PROMPT_REVIEW_QUEUE)
    public void consume(PromptReviewMessage message) {
        Prompt prompt = promptMapper.selectById(message.promptId());
        if (prompt == null) {
            log.info("skip review message because prompt does not exist, promptId={}", message.promptId());
            return;
        }
        //校验是否被更改过
        if (!Objects.equals(prompt.getVersion(), message.expectedVersion())) {
            log.info("skip stale review message, promptId={}, expectedVersion={}, dbversion={}",
                    message.promptId(), message.expectedVersion(), prompt.getVersion());
            return;
        }
        //校验是否在审查中
        if (prompt.getStatus() != PromptStatus.REVIEWING) {
            log.info("skip review message because prompt status is not reviewing, promptId={}, status={}",
                    prompt.getId(), prompt.getStatus());
            return;
        }

        try {
            reviewChain.review(prompt);
            promptMapper.updateStatusByIdAndVersion(
                    prompt.getId(),
                    message.expectedVersion(),
                    PromptStatus.REVIEWING,
                    PromptStatus.ENABLED
            );
            //删除旧的缓存内容
            redisCacheService.deletePromptCache(prompt.getId());
            log.info("prompt review passed, promptId={}", prompt.getId());
        } catch (BusinessException ex) {
            promptMapper.updateStatusByIdAndVersion(
                    prompt.getId(),
                    message.expectedVersion(),
                    PromptStatus.REVIEWING,
                    PromptStatus.REJECTED
            );
            redisCacheService.deletePromptCache(prompt.getId());
            log.info("prompt review rejected, promptId={}, reason={}", prompt.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("prompt review failed and will be retried, promptId={}", prompt.getId(), ex);
            throw ex;
        }
    }
}
