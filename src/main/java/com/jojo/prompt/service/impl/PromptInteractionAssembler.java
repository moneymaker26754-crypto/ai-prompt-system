package com.jojo.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jojo.prompt.converter.PromptConverter;
import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.entity.PromptFavorite;
import com.jojo.prompt.entity.PromptLike;
import com.jojo.prompt.mapper.PromptFavoriteMapper;
import com.jojo.prompt.mapper.PromptLikeMapper;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptInteractionAssembler {

    private final PromptLikeMapper promptLikeMapper;
    private final PromptFavoriteMapper promptFavoriteMapper;
    private final PromptConverter promptConverter;
    private final RedisCacheService redisCacheService;
    private final PromptPermissionService promptPermissionService;


    public void fillUserStatus(List<PromptVO> voList, List<Prompt> prompts) {
        Long userId = promptPermissionService.requireCurrentUserId();
        if (userId == null) {
            voList.forEach(v -> v.setIsFavorite(false));
            voList.forEach(v -> v.setIsLike(false));
            return;
        }

        List<Long> promptIds = prompts.stream()
                .map(Prompt::getId)
                .collect(Collectors.toList());
        if (promptIds.isEmpty()) {
            voList.forEach(v -> v.setIsFavorite(false));
            voList.forEach(v -> v.setIsLike(false));
            return;
        }

        List<PromptFavorite> favorites = promptFavoriteMapper.selectList(
                new LambdaQueryWrapper<PromptFavorite>()
                        .eq(PromptFavorite::getUserId, userId)
                        .in(PromptFavorite::getPromptId, promptIds)
        );
        List<PromptLike> likes = promptLikeMapper.selectList(
                new LambdaQueryWrapper<PromptLike>()
                        .eq(PromptLike::getUserId, userId)
                        .in(PromptLike::getPromptId, promptIds)
        );

        Set<Long> favoriteIds = favorites.stream()
                .map(PromptFavorite::getPromptId)
                .collect(Collectors.toSet());
        Set<Long> likeIds = likes.stream()
                .map(PromptLike::getPromptId)
                .collect(Collectors.toSet());

        voList.forEach(v -> v.setIsFavorite(favoriteIds.contains(v.getId())));
        voList.forEach(v -> v.setIsLike(likeIds.contains(v.getId())));
    }

    //辅助收藏和点赞分页逻辑,2.0改进
    public List<PromptVO> fillFavoriteAndLikeStatus(List<Prompt> prompts) {
        if(prompts == null || prompts.isEmpty()) {
            return Collections.emptyList();
        }
        List<PromptVO> voList = promptConverter.toVOList(prompts);
        Map<Long, PromptVO> voMap = voList.stream().collect(Collectors.toMap(PromptVO::getId, vo -> vo));

        prompts.forEach(prompt -> {
            PromptVO vo = voMap.get(prompt.getId());
            if(vo != null) {
                mergeRedisCountsToVO(vo, prompt.getId());
            }
        });
        fillUserStatus(voList, prompts);
        return voList;
    }

    //计数器的下限保护
    private int mergeCount(Integer base, Long delta) {
        int result = (base == null ? 0 : base) + (delta == null ? 0 : delta.intValue());
        return Math.max(result, 0);
    }

    public void mergeRedisCountsToVO(PromptVO vo, Long promptId) {
        try {
            Map<String, Long> counts = redisCacheService.getPromptCounts(promptId);
            vo.setViewCount(mergeCount(vo.getViewCount(), counts.get("viewCount")));
            vo.setLikeCount(mergeCount(vo.getLikeCount(), counts.get("likeCount")));
            vo.setFavoriteCount(mergeCount(vo.getFavoriteCount(), counts.get("favoriteCount")));
            vo.setCopyCount(mergeCount(vo.getCopyCount(), counts.get("copyCount")));
        } catch (Exception e) {
            log.warn("merge redis counts failed, use db counts, promptId={}", promptId, e);
        }
    }
}
