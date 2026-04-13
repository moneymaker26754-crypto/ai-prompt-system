package com.jojo.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jojo.prompt.common.event.PromptFavoriteEvent;
import com.jojo.prompt.common.event.PromptHeatEvent;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.result.PageResult;
import com.jojo.prompt.dto.response.PromptFavoriteListItem;
import com.jojo.prompt.dto.response.PromptFavoriteVO;
import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.entity.PromptFavorite;
import com.jojo.prompt.mapper.PromptFavoriteMapper;
import com.jojo.prompt.service.PromptFavoriteService;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptFavoriteServiceImpl implements PromptFavoriteService {

    private final PromptFavoriteMapper promptFavoriteMapper;
    private final RedisCacheService redisCacheService;
    private final PromptPermissionService promptPermissionService;
    //事件监听器，注入事件发布器
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoritePrompt(Long id) {
        //获取用户id
        Long userId = promptPermissionService.requireCurrentUserId();
        //提示词是否存在
        Prompt prompt = promptPermissionService.validatePromptExists(id, userId);
        if(prompt == null){
            throw new BusinessException("prompt not exist");
        }
        //查询缓存
        if(redisCacheService.isUserFavorite(userId, id)){
            log.info("Prompt has been favorite, idempotent return: userId={}, promptId={}", userId, id);
            return;
        }
        //提示词是否已收藏
        boolean isFavorite = isFavoritePrompt(id, userId);
        if (isFavorite) {
            //补充缓存
            redisCacheService.addUserFavorite(userId, id);
            log.info("Prompt has been favorite, idempotent return: userId={}, promptId={}", userId, id);
            return;
        }
        //创建收藏记录
        PromptFavorite promptFavorite = new PromptFavorite();
        promptFavorite.setUserId(userId);
        promptFavorite.setPromptId(id);
        try {
            promptFavoriteMapper.insert(promptFavorite);
            //更新redis提示词收藏数，和用户行为缓存
            redisCacheService.incrementFavoriteCount(id);
            redisCacheService.addUserFavorite(userId, id);
        } catch (DuplicateKeyException e) {
            log.info("Prompt has been liked concurrently, userId={}, promptId={}", userId, id);
            //补充缓存
            redisCacheService.addUserFavorite(userId, id);
            return;
        }

        //删除提示词详情缓存
        redisCacheService.deletePromptCache(id);

        log.info("favorite success, userId={}, promptId={}", userId, id);

        String type = "favorite";

        publish(id, userId, type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavoritePrompt(Long id) {
        Long userId = promptPermissionService.requireCurrentUserId();
        //查询收藏记录
        PromptFavorite favorite = promptFavoriteMapper.selectOne(
                new LambdaQueryWrapper<PromptFavorite>()
                        .eq(PromptFavorite::getUserId, userId)
                        .eq(PromptFavorite::getPromptId, id)
        );
        if (favorite == null) {
            log.info("the prompt has not been favorited, idempotent return: userId={}, promptId={}", userId, id);
            return;
        }
        //逻辑删除
        promptFavoriteMapper.deleteById(favorite.getId());

        //更新redis提示词收藏数，和用户行为缓存
        redisCacheService.decrementFavoriteCount(id);
        redisCacheService.removeUserFavorite(userId, id);

        //删除提示词详情缓存
        redisCacheService.deletePromptCache(id);

        log.info("unfavorite success, userId={}, promptId={}", userId, id);

        String type = "unfavorite";

        publish(id, userId, type);

    }


    @Override
    public PageResult<PromptFavoriteVO> queryMyFavoritePrompt(int pageNo, int pageSize) {
        Long userId = promptPermissionService.requireCurrentUserId();
        //分页查询收藏记录
        Page<PromptFavoriteListItem> page = new Page<>(pageNo, pageSize);
        //一次返回全部结果，防止N+1问题
        Page<PromptFavoriteListItem> favoritePage = promptFavoriteMapper.selectMyFavoritePromptPage(page, userId);
        //查询提示词详细
        List<PromptFavoriteVO> voList = favoritePage.getRecords().stream()
                .map(item -> {
                    PromptVO promptVO = new PromptVO();
                    promptVO.setId(item.getPromptId());
                    promptVO.setTitle(item.getTitle());
                    promptVO.setContent(item.getContent());
                    promptVO.setViewCount(item.getViewCount());
                    promptVO.setLikeCount(item.getLikeCount());
                    promptVO.setFavoriteCount(item.getFavoriteCount());
                    promptVO.setCopyCount(item.getCopyCount());
                    promptVO.setUserId(item.getUserId());
                    promptVO.setCategoryId(item.getCategoryId());
                    promptVO.setCategoryName(item.getCategoryName());
                    promptVO.setIsFavorite(true);
                    if(item.getTags() != null && !item.getTags().isBlank()) {
                        promptVO.setTagList(Arrays.asList(item.getTags().split(",")));
                    }else {
                        promptVO.setTagList(Collections.emptyList());
                    }
                    PromptFavoriteVO vo = new PromptFavoriteVO();
                    vo.setFavoriteId(item.getFavoriteId());
                    vo.setFavoriteTime(item.getFavoriteTime());
                    vo.setPromptVO(promptVO);
                    return vo;
                })
                .toList();

        return PageResult.of(
                favoritePage.getCurrent(),
                favoritePage.getSize(),
                favoritePage.getTotal(),
                voList
        );
    }

    @Override
    public boolean isFavoritePrompt(Long id, Long userId) {
        //先查redis
        if(redisCacheService.isUserFavorite(userId, id)){
            return true;
        }
        Long count = promptFavoriteMapper.selectCount(
                new LambdaQueryWrapper<PromptFavorite>()
                        .eq(PromptFavorite::getUserId, userId)
                        .eq(PromptFavorite::getPromptId, id)
        );

        // 补充缓存
        if (count > 0) {
            redisCacheService.addUserFavorite(userId, id);
        }

        return count > 0;
    }

    //辅助发布事件
    private void publish(Long id, Long userId, String type) {
        //发布点赞事件
        PromptHeatEvent event = new PromptHeatEvent(id, userId, type, LocalDateTime.now());
        eventPublisher.publishEvent(event);
        log.info("publish like event: promptID={}, userId={}", id, userId);
    }
}
