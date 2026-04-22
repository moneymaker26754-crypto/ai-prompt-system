package com.jojo.prompt.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.common.event.PromptHeatEvent;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.result.PageResult;
import com.jojo.prompt.common.utils.RequestIdentityUtil;
import com.jojo.prompt.converter.PromptConverter;
import com.jojo.prompt.dto.request.PromptQueryDTO;
import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Category;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.mapper.CategoryMapper;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.jojo.prompt.common.constant.RedisKeyConstant.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptQueryServiceImpl implements PromptQueryService {

    private final PromptMapper promptMapper;
    private final CategoryMapper categoryMapper;
    private final PromptLikeService promptLikeService;
    private final PromptFavoriteService promptFavoriteService;
    private final SearchHistoryService searchHistoryService;
    private final PromptConverter promptConverter;
    private final RedisCacheService redisCacheService;
    private final PromptInteractionAssembler promptInteractionAssembler;
    private final PromptPermissionService promptPermissionService;
    //事件监视器
    private final ApplicationEventPublisher eventPublisher;


    //2.0引入redis，应对缓存穿透和雪崩，缓存读写分离做降级策略
    @Override
    public PromptVO queryPromptById(Long id) {
        Long currentUserId = promptPermissionService.getCurrentUserIdOrNull();
//        if (redisCacheService.isPromptNullCache(id)) {
//            throw new BusinessException(404, "prompt not exist");
//        }

        boolean nullCache = redisRead("prompt-null-check",
                () -> redisCacheService.isPromptNullCache(id), false);
        if(nullCache){
            throw new BusinessException(404, "prompt not exist");
        }
        //先查询缓存
        PromptVO cacheVO = redisRead("prompt-detail-get",
                () -> redisCacheService.getPromptDetailCache(id), null);
//        PromptVO cacheVO = redisCacheService.getPromptDetailCache(id);
        if (cacheVO != null) {
            //命中缓存拷贝对象，防止污染缓存
            PromptVO result = BeanUtil.copyProperties(cacheVO, PromptVO.class);
            //非本人查询才计数viewCount
            boolean owner = currentUserId != null && currentUserId.equals(result.getUserId());
            if (!owner) {
//                redisCacheService.incrementViewCount(id);
                redisWrite("view-count-increment", () -> redisCacheService.incrementViewCount(id));
            }
            //无论是否owner，都merge实时计数
            promptInteractionAssembler.mergeRedisCountsToVO(result, id);
            //设置补充当前用户的点赞/收藏状态
            if (currentUserId != null) {
                result.setIsLike(promptLikeService.isLiked(id, currentUserId));
                result.setIsFavorite(promptFavoriteService.isFavoritePrompt(id, currentUserId));
            } else {
                result.setIsLike(false);
                result.setIsFavorite(false);
            }
            //事件发布，更新热度
            String action = "view";
            eventPublisher.publishEvent(
                    new PromptHeatEvent(
                            id,
                            currentUserId,
                            action,
                            LocalDateTime.now()
                    )
            );

            return result;
        }
        //缓存未命中，查数据库
        Prompt prompt = promptMapper.selectById(id);
        if (prompt == null) {
            redisWrite("prompt-null-cache", () -> redisCacheService.cachePromptNull(id));
            throw new BusinessException(404, "prompt not exist");
        }
        boolean owner = currentUserId != null && currentUserId.equals(prompt.getUserId());
        //如果不是当前用户的ID则不可以访问私有和禁止的数据
        if (!owner) {
            if (prompt.getStatus() != PromptStatus.ENABLED) {
                throw new BusinessException(404, "prompt not exist");
            }
            if (prompt.getVisibility() != PromptVisibility.PUBLIC) {
                throw new BusinessException(404, "prompt not exist");//隐藏资源存在性
            }
        }
        PromptVO vo = promptConverter.toVO(prompt, categoryMapper.selectById(prompt.getCategoryId()));
        //非本人查询才计数viewCount
        if (!owner) {
//            redisCacheService.incrementViewCount(id);
            redisWrite("view-count-increment", () -> redisCacheService.incrementViewCount(id));

        }
        //无论是否owner，都merge实时计数
        promptInteractionAssembler.mergeRedisCountsToVO(vo, id);
        if (currentUserId != null) {
            vo.setIsLike(promptLikeService.isLiked(id, currentUserId));
            vo.setIsFavorite(promptFavoriteService.isFavoritePrompt(id, currentUserId));
        } else {
            vo.setIsLike(false);
            vo.setIsFavorite(false);
        }
        //只用公开的和启用的用户才能写入缓存
        if (prompt.getVisibility() == PromptVisibility.PUBLIC && prompt.getStatus() == PromptStatus.ENABLED) {
            redisWrite("prompt-detail-cache", () -> redisCacheService.cachePromptDetail(id, vo));
        }

        return vo;
    }

    @Override
    public PageResult<PromptVO> queryPage(PromptQueryDTO query, Integer pageNo, Integer pageSize, HttpServletRequest request) {
        applySearchRateLimit(request);
        if (StringUtils.hasText(query.getKeyword())) {
            searchHistoryService.recordHistory(query.getKeyword());
        }

        Page<Prompt> page = new Page<>(pageNo, pageSize);
        if (StringUtils.hasText(query.getKeyword())) {
            return searchPublicWithFullText(query, pageNo, pageSize);
        }
        LambdaQueryWrapper<Prompt> wrapper = buildPublicQueryWrapper(query);
        Page<Prompt> promptPage = promptMapper.selectPage(page, wrapper);

        List<PromptVO> voList = promptInteractionAssembler.fillFavoriteAndLikeStatus(promptPage.getRecords());

        return PageResult.of(
                promptPage.getCurrent(),
                promptPage.getSize(),
                promptPage.getTotal(),
                voList
        );
    }



    @Override
    public PageResult<PromptVO> queryMyPage(PromptQueryDTO query, Integer pageNo, Integer pageSize,  HttpServletRequest request) {
        applySearchRateLimit(request);
        Long userId = promptPermissionService.requireCurrentUserId();
        Page<Prompt> page = new Page<>(pageNo, pageSize);

        if (StringUtils.hasText(query.getKeyword())) {
            return searchWithMyFullText(query, userId, pageNo, pageSize);
        }

        LambdaQueryWrapper<Prompt> wrapper = buildMyQueryWrapper(query, userId);

        Page<Prompt> promptPage = promptMapper.selectPage(page, wrapper);
        List<PromptVO> voList = promptInteractionAssembler.fillFavoriteAndLikeStatus(promptPage.getRecords());
        return PageResult.of(
                promptPage.getCurrent(),
                promptPage.getSize(),
                promptPage.getTotal(),
                voList
        );
    }

    public List<PromptVO> getHotList(String type, int limit) {
        //2.0不污染热门榜单浏览量的方法
        //try-catch包围，缓存失效时查询数据库
        List<Long> hotIds;
        try {
            hotIds = redisCacheService.getHotRanking(type, limit);
        } catch (Exception e) {
            log.warn("get hot ranking from redis failed, fallback db, type={}, limit={}", type, limit, e);
            return getHotListFromDb(type, limit);
        }
        if (hotIds == null || hotIds.isEmpty()) {
            log.info("hot ranking cache empty, fallback db, type={}, limit={}", type, limit);
            return getHotListFromDb(type, limit);
        }

        List<Prompt> prompts = promptMapper.selectByIds(hotIds);
        if (prompts == null || prompts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Prompt> promptMap = prompts.stream()
                .filter(prompt -> prompt.getVisibility() == PromptVisibility.PUBLIC
                        && prompt.getStatus() == PromptStatus.ENABLED)
                .collect(Collectors.toMap(Prompt::getId, prompt -> prompt));

        List<Long> orderPromptIds = hotIds.stream()
                .filter(promptMap::containsKey)
                .toList();
        if(orderPromptIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> categoryIds = orderPromptIds.stream()
                .map(promptMap::get)
                .map(Prompt::getCategoryId)
                .collect(Collectors.toSet());

        Map<Long, Category> categoryMap = categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        List<Prompt> orderPrompts = orderPromptIds.stream()
                .map(promptMap::get)
                .collect(Collectors.toList());
        List<PromptVO> voList = orderPrompts.stream()
                .map(prompt -> {
                    Category category = categoryMap.get(prompt.getCategoryId());
                    PromptVO vo = promptConverter.toVO(prompt);
                    promptInteractionAssembler.mergeRedisCountsToVO(vo, prompt.getId());
                    return vo;
                }).collect(Collectors.toList());

        promptInteractionAssembler.fillUserStatus(voList, orderPrompts);
        return voList;
    }


    //缓存失效时回调数据库的降级策略
    private List<PromptVO> getHotListFromDb(String type, int limit) {
        LambdaQueryWrapper<Prompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Prompt::getVisibility, PromptVisibility.PUBLIC);
        wrapper.eq(Prompt::getStatus, PromptStatus.ENABLED);

        switch (type) {
            case "like" -> wrapper.orderByDesc(Prompt::getLikeCount);
            case "favorite" -> wrapper.orderByDesc(Prompt::getFavoriteCount);
            case "copy" -> wrapper.orderByDesc(Prompt::getCopyCount);
            default -> wrapper.orderByDesc(Prompt::getViewCount);
        }
        wrapper.orderByDesc(Prompt::getCreateTime);
        wrapper.last("limit " + limit);

        List<Prompt> prompts = promptMapper.selectList(wrapper);
        if (prompts == null || prompts.isEmpty()) {
            return Collections.emptyList();
        }

        List<PromptVO> voList = prompts.stream()
                .map(prompt -> {
                    PromptVO vo = promptConverter.toVO(prompt);
                    promptInteractionAssembler.mergeRedisCountsToVO(vo, prompt.getId());
                    return vo;
                })
                .toList();

        promptInteractionAssembler.fillUserStatus(voList, prompts);
        return voList;
    }

    //辅助查询条件公共的分页逻辑
    public LambdaQueryWrapper<Prompt> buildPublicQueryWrapper(PromptQueryDTO query) {
        LambdaQueryWrapper<Prompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Prompt::getVisibility, PromptVisibility.PUBLIC);
        wrapper.eq(Prompt::getStatus, PromptStatus.ENABLED);
        appendCommonFilter(wrapper, query);
        appendSort(wrapper, query);
        return wrapper;
    }

    //辅助查询条件我的的分页逻辑
    public LambdaQueryWrapper<Prompt> buildMyQueryWrapper(PromptQueryDTO query, Long userId) {
        LambdaQueryWrapper<Prompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Prompt::getUserId, userId);
        if (query.getVisibility() != null) {
            wrapper.eq(Prompt::getVisibility, query.getVisibility());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Prompt::getStatus, query.getStatus());
        }
        appendCommonFilter(wrapper, query);
        appendSort(wrapper, query);
        return wrapper;
    }

    //公共查询条件
    private void appendCommonFilter(LambdaQueryWrapper<Prompt> wrapper, PromptQueryDTO query) {
        if (query.getCategoryId() != null) {
            wrapper.eq(Prompt::getCategoryId, query.getCategoryId());
        }
        if (StringUtils.hasText(query.getTag())) {
            wrapper.like(Prompt::getTags, query.getTag());
        }
    }

    //公共排序条件
    private void appendSort(LambdaQueryWrapper<Prompt> wrapper, PromptQueryDTO query) {
        if ("viewCount".equals(query.getSortField())) {
            wrapper.orderBy(true, "asc".equals(query.getSortOrder()), Prompt::getViewCount);
        } else if ("copyCount".equals(query.getSortField())) {
            wrapper.orderBy(true, "asc".equals(query.getSortOrder()), Prompt::getCopyCount);
        } else {
            wrapper.orderByDesc(Prompt::getCreateTime);
        }
    }

    //1.5关键词的索引进行全文搜索，我的搜索
    private PageResult<PromptVO> searchWithMyFullText(PromptQueryDTO query, Long userId, int pageNo, int pageSize) {
        Page<Prompt> page = new Page<>(pageNo, pageSize);
        Page<Prompt> promptPage = promptMapper.searchMyFullText(page, userId, query.getKeyword());
        List<PromptVO> voList = promptInteractionAssembler.fillFavoriteAndLikeStatus(promptPage.getRecords());
        return PageResult.of(
                promptPage.getCurrent(),
                promptPage.getSize(),
                promptPage.getTotal(),
                voList
        );
    }

    //1.5关键词的索引进行全文搜索，z公共搜索
    private PageResult<PromptVO> searchPublicWithFullText(PromptQueryDTO query, int pageNo, int pageSize) {
        Page<Prompt> page = new Page<>(pageNo, pageSize);
        Page<Prompt> promptPage = promptMapper.searchPublicFullText(page, query.getKeyword(), PromptVisibility.PUBLIC, PromptStatus.ENABLED);
        List<PromptVO> voList = promptInteractionAssembler.fillFavoriteAndLikeStatus(promptPage.getRecords());
        return PageResult.of(
                promptPage.getCurrent(),
                promptPage.getSize(),
                promptPage.getTotal(),
                voList
        );
    }

    //搜索限流
    private void applySearchRateLimit(HttpServletRequest request) {
        Long currentUserId = promptPermissionService.getCurrentUserIdOrNull();
        String identifier = RequestIdentityUtil.buildSearchIdentifier(currentUserId, request);
        boolean allowed = redisCacheService.trySearchAllowed(
                identifier,
                SEARCH_LIMIT_PRE_MINUTE,
                RATE_LIMIT_EXPIRE_1MIN
        );
        if(!allowed) {
            throw new BusinessException(429, "search too frequent, please try again later");
        }
    }

    //搜索降级
    private <T> T redisRead(String scene, Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        }catch (Exception e) {
            log.warn("redis read failed, scene={}, fallback used", scene, e);
            return fallback;
        }
    }
    private void redisWrite(String scene, Runnable runnable) {
        try {
            runnable.run();
        }catch (Exception e) {
            log.warn("redis write failed, scene={}, ignored",  scene, e);
        }
    }
}
