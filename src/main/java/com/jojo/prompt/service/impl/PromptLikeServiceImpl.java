package com.jojo.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.utils.UserContext;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.entity.PromptLike;
import com.jojo.prompt.mapper.PromptLikeMapper;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.PromptLikeService;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptLikeServiceImpl implements PromptLikeService {

    private final PromptLikeMapper promptLikeMapper;
    private final PromptMapper promptMapper;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likePrompt(Long id) {
        //获取当前用户
        Long userId = requireCurrentUserId();
        //校验提示词
        Prompt prompt = validatePromptExists(id, userId);
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unLikePrompt(Long id) {
        Long userId = requireCurrentUserId();
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


    //辅助权限检查，查看是否登录
    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "not logged in, please log in first");
        }
        return userId;
    }

    //提示词，用户状态和可见性检查
    private Prompt validatePromptExists(Long promptId, Long userId) {
        Prompt prompt = promptMapper.selectById(promptId);
        if (prompt == null) {
            throw new BusinessException("prompt not exist");
        }
        if (prompt.getStatus() != PromptStatus.ENABLED) {
            throw new BusinessException("prompt not exist");
        }
        if (prompt.getVisibility() == PromptVisibility.PRIVATE
                && !userId.equals(prompt.getUserId())) {
            throw new BusinessException(403, "no permission to like this prompt");
        }
        if (userId.equals(prompt.getUserId())) {
            throw new BusinessException(400, "cannot like your own prompt");
        }
        return prompt;
    }
}
