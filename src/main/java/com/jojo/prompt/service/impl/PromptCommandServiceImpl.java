package com.jojo.prompt.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.common.event.PromptCreateEvent;
import com.jojo.prompt.common.event.PromptHeatEvent;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.handler.PromptReviewHandler;
import com.jojo.prompt.common.mq.message.PromptReviewMessage;
import com.jojo.prompt.common.mq.producer.PromptMqProducer;
import com.jojo.prompt.common.utils.RequestIdentityUtil;
import com.jojo.prompt.dto.request.PromptCreateDTO;
import com.jojo.prompt.dto.request.PromptUpdateDTO;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.PromptCommandService;
import com.jojo.prompt.service.RedisCacheService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static com.jojo.prompt.common.constant.RedisKeyConstant.COPY_DEDUP_WINDOW_SECONDS;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptCommandServiceImpl implements PromptCommandService {

    private final PromptMapper promptMapper;
    private final RedisCacheService redisCacheService;
    private final PromptPermissionService promptPermissionService;
    //2.5版责任链模式
    private final PromptReviewHandler reviewChain;
    private final ApplicationEventPublisher eventPublisher;
    //消息队列
    private final PromptMqProducer promptMqProducer;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPrompt(PromptCreateDTO dto) {
        Long userId = promptPermissionService.requireCurrentUserId();
        promptPermissionService.validateCategoryExists(dto.getCategoryId());

        Prompt prompt = BeanUtil.copyProperties(dto, Prompt.class);
        //通过写入数据
        prompt.setUserId(userId);
        prompt.setStatus(PromptStatus.REVIEWING);
        prompt.setViewCount(0);
        prompt.setCopyCount(0);
        prompt.setLikeCount(0);
        prompt.setFavoriteCount(0);
        prompt.setVersion(1);

        //写入数据库
        promptMapper.insert(prompt);
        //发布消息
        eventPublisher.publishEvent(new PromptCreateEvent(
                prompt.getId(),
                userId,
                prompt.getTitle(),
                LocalDateTime.now()
        ));
        //先创建成功通过消息队列，异步实现审核链
        String key = "CREATE";
        //当前事务真正提交成功才执行这个回调，否则可能会造成消息和数据库状态不一致
        afterCommit(() -> promptMqProducer.sendPromptReviewMessage(
                new PromptReviewMessage(
                        prompt.getId(),
                        prompt.getVersion(),
                        key,
                        userId,
                        LocalDateTime.now()
                )
        ));

        return prompt.getId();
    }

    @Override
    public void updatePrompt(PromptUpdateDTO dto) {
        Prompt existing = promptMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("prompt not exist");
        }

        Long userId = promptPermissionService.requireCurrentUserId();
        if (!userId.equals(existing.getUserId())) {
            throw new BusinessException(403, "no permission to modify this prompt");
        }
        promptPermissionService.validateCategoryExists(dto.getCategoryId());

        Prompt prompt = BeanUtil.copyProperties(dto, Prompt.class);

        //删除旧缓存
        redisCacheService.deletePromptCache(dto.getId());

        //通过消息队列，异步审核更新操作
        String key = "UPDATE";
        afterCommit(() -> promptMqProducer.sendPromptReviewMessage(
                new PromptReviewMessage(prompt.getId(),
                        prompt.getVersion(),
                        key,
                        userId,
                        LocalDateTime.now()
                )
        ));
    }

    @Override
    public void deletePrompt(Long id) {
        Prompt prompt = promptMapper.selectById(id);
        if (prompt == null) {
            throw new BusinessException("prompt not exist");
        }

        Long userId = promptPermissionService.requireCurrentUserId();
        if (!userId.equals(prompt.getUserId())) {
            throw new BusinessException(403, "no permission to delete this prompt");
        }
        promptMapper.deleteById(id);
        //删除旧缓存
        redisCacheService.deletePromptCache(id);
    }

    public String copyPrompt(Long id, HttpServletRequest request) {
        Prompt prompt = promptMapper.selectById(id);
        if (prompt == null) {
            throw new BusinessException(404, "prompt not exist");
        }

        Long currentUserId = promptPermissionService.getCurrentUserIdOrNull();
        boolean owner = currentUserId != null && currentUserId.equals(prompt.getUserId());
        if (!owner) {
            if (prompt.getStatus() != PromptStatus.ENABLED) {
                throw new BusinessException(404, "prompt not exist");
            }
            if (prompt.getVisibility() != PromptVisibility.PUBLIC) {
                throw new BusinessException(404, "prompt not exist");
            }
        }
        //复制去重，基于用户和请求唯一标识，一分钟内相同用户相同请求只记录一次复制，防止重复点击导致复制数暴涨
        String identifier = RequestIdentityUtil.buildCopyIdentifier(currentUserId, request);
        boolean firstCopyInWindow = redisCacheService.tryRecordCopyCount(
                identifier,
                id,
                COPY_DEDUP_WINDOW_SECONDS);
        if (firstCopyInWindow) {
            redisCacheService.incrementCopyCount(id);
            log.info("copy prompt success: promptId:{}, copyCount:{}", id, prompt.getCopyCount());
        }

        //事件发布，更新热度
        String action = "copy";
        eventPublisher.publishEvent(
                new PromptHeatEvent(
                        id,
                        currentUserId,
                        action,
                        LocalDateTime.now()
                )
        );
        return prompt.getContent();
    }

    private void afterCommit(Runnable task) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }
}
