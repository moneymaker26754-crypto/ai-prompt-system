package com.jojo.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jojo.prompt.common.event.PromptLikeEvent;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.entity.PromptLike;
import com.jojo.prompt.mapper.PromptLikeMapper;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.PromptLikeService;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptLikeServiceImpl implements PromptLikeService {

    private final PromptLikeMapper promptLikeMapper;
    private final RedisCacheService redisCacheService;
    private final PromptPermissionService promptPermissionService;
    //事件监听器,注入事件发布器
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likePrompt(Long id) {
        //获取当前用户
        Long userId = promptPermissionService.requireCurrentUserId();
        //校验提示词
        Prompt prompt = promptPermissionService.validatePromptExists(id, userId);
        if(prompt == null){
            throw new BusinessException("prompt not exist");
        }
        //查询缓存
        if(redisCacheService.isUserLiked(userId, id)) {
            log.info("Prompt has been liked, idempotent return: userId={}, promptId={}", userId, id);
            return;
        }
        //检查数据库
        boolean isLike = isLiked(id, userId);
        //补充缓存
        if (isLike) {
            redisCacheService.addUserLike(userId, id);
            log.info("Prompt has been liked, idempotent return: userId={}, promptId={}", userId, id);
            return;
        }
        //查询数据库，添加喜欢
        PromptLike promptLike = new PromptLike();
        promptLike.setUserId(userId);
        promptLike.setPromptId(id);
        try {
            promptLikeMapper.insert(promptLike);
            //更新redis计数和用户行为缓存
            redisCacheService.incrementLikeCount(id);
            redisCacheService.addUserLike(userId, id);
        } catch (DuplicateKeyException e) {
            log.info("Prompt has been liked concurrently, userId={}, promptId={}", userId, id);
            //补充缓存
            redisCacheService.addUserLike(userId, id);
            return;
        }

        //删除提示词详情缓存
        redisCacheService.deletePromptCache(id);

        log.info("Like success, userId={}, promptId={}", userId, id);

        //发布点赞事件
        PromptLikeEvent event = new PromptLikeEvent(id, userId, prompt.getUserId(), LocalDateTime.now());
        eventPublisher.publishEvent(event);
        log.info("publish like event: promptID={}, userId={}", prompt.getUserId(), prompt.getUserId());

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unLikePrompt(Long id) {
        Long userId = promptPermissionService.requireCurrentUserId();
        //查询喜欢记录
        PromptLike promptLike = promptLikeMapper.selectOne(
                new LambdaQueryWrapper<PromptLike>()
                        .eq(PromptLike::getUserId, userId)
                        .eq(PromptLike::getPromptId, id)
        );
        if (promptLike == null) {
            log.info("the prompt has not been liked, idempotent return: userId={}, promptId={}", userId, id);
            return;
        }
        promptLikeMapper.deleteById(promptLike.getId());

        //更新redis计数和用户行为缓存
        redisCacheService.decrementLikeCount(id);
        redisCacheService.removeUserLike(userId, id);

        //删除提示词详情缓存
        redisCacheService.deletePromptCache(id);

        log.info("Unlike success, userId={}, promptId={}", userId, id);
    }

    @Override
    public boolean isLiked(Long id, Long userId) {
        //先查redis
        if(redisCacheService.isUserLiked(userId, id)) {
            return true;
        }
        Long count = promptLikeMapper.selectCount(
                new LambdaQueryWrapper<PromptLike>()
                        .eq(PromptLike::getUserId, userId)
                        .eq(PromptLike::getPromptId, id)
        );
        //补充缓存
        if(count > 0) {
            redisCacheService.addUserLike(userId, id);
        }

        return count > 0;
    }


}
