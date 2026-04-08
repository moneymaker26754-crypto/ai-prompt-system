package com.jojo.prompt.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.result.PageResult;
import com.jojo.prompt.common.utils.UserContext;
import com.jojo.prompt.converter.PromptConverter;
import com.jojo.prompt.dto.request.PromptCreateDTO;
import com.jojo.prompt.dto.request.PromptQueryDTO;
import com.jojo.prompt.dto.request.PromptUpdateDTO;
import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.entity.PromptFavorite;
import com.jojo.prompt.entity.PromptLike;
import com.jojo.prompt.mapper.CategoryMapper;
import com.jojo.prompt.mapper.PromptFavoriteMapper;
import com.jojo.prompt.mapper.PromptLikeMapper;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptMapper promptMapper;
    private final CategoryMapper categoryMapper;
    private final PromptConverter promptConverter;
    private final PromptFavoriteService promptFavoriteService;
    private final PromptFavoriteMapper promptFavoriteMapper;
    private final PromptLikeService promptLikeService;
    private final PromptLikeMapper promptLikeMapper;
    private final SearchHistoryService searchHistoryService;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPrompt(PromptCreateDTO dto) {
        Long userId = requireCurrentUserId();
        validateCategoryExists(dto.getCategoryId());

        Prompt prompt = BeanUtil.copyProperties(dto, Prompt.class);
        prompt.setUserId(userId);
        prompt.setStatus(PromptStatus.ENABLED);
        prompt.setViewCount(0);
        prompt.setCopyCount(0);
        prompt.setVersion(1);

        promptMapper.insert(prompt);
        return prompt.getId();
    }

    @Override
    public void updatePrompt(PromptUpdateDTO dto) {
        Prompt existing = promptMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("prompt not exist");
        }

        Long userId = requireCurrentUserId();
        if (!userId.equals(existing.getUserId())) {
            throw new BusinessException(403, "no permission to modify this prompt");
        }
        validateCategoryExists(dto.getCategoryId());

        Prompt prompt = BeanUtil.copyProperties(dto, Prompt.class);
        int rows = promptMapper.updateById(prompt);
        if (rows == 0) {
            throw new BusinessException("prompt has been modified by another user");
        }
        //删除缓存
        redisCacheService.deletePromptCache(dto.getId());
    }

    @Override
    public void deletePrompt(Long id) {
        Prompt prompt = promptMapper.selectById(id);
        if (prompt == null) {
            throw new BusinessException("prompt not exist");
        }

        Long userId = requireCurrentUserId();
        if (!userId.equals(prompt.getUserId())) {
            throw new BusinessException(403, "no permission to delete this prompt");
        }
        promptMapper.deleteById(id);
        //删除缓存
        redisCacheService.deletePromptCache(id);
    }

    //2.0引入redis，还得加入互斥锁，解决缓存击穿
    @Override
    public PromptVO queryPromptById(Long id) {
        Long currentUserId = UserContext.getUserId();
        if(redisCacheService.isPromptNullCache(id)) {
            throw new BusinessException(404, "prompt not exist");
        }
        //先查询缓存
        PromptVO cacheVO = redisCacheService.getPromptDetailCache(id);
        if(cacheVO != null) {
            //命中缓存
            //非本人查询才计数viewCount
            boolean owner = currentUserId != null && currentUserId.equals(cacheVO.getUserId());
            if (!owner) {
                redisCacheService.incrementViewCount(id);
            }
            //设置补充当前用户的点赞/收藏状态
            if(currentUserId != null) {
                cacheVO.setIsLike(redisCacheService.isUserLiked(currentUserId, id));
                cacheVO.setIsFavorite(redisCacheService.isUserFavorite(currentUserId, id));
            }else {
                cacheVO.setIsLike(false);
                cacheVO.setIsFavorite(false);
            }

            return cacheVO;
        }
        //缓存未命中，查数据库
        Prompt prompt = promptMapper.selectById(id);
        if (prompt == null) {
            redisCacheService.cachePromptNull(id);
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
        //非本人查询才计数viewCount
        if (!owner) {
            promptMapper.incrementViewCount(id);
            prompt = promptMapper.selectById(id);
        }

        PromptVO vo = promptConverter.toVO(prompt, categoryMapper.selectById(prompt.getCategoryId()));
        //只用公开的和启用的用户才能写入缓存
        if(prompt.getVisibility() == PromptVisibility.PUBLIC && prompt.getStatus() ==  PromptStatus.ENABLED) {
            redisCacheService.cachePromptDetail(id, vo);
        }

        return vo;
    }

    @Override
    public PageResult<PromptVO> queryPage(PromptQueryDTO query, Integer pageNo, Integer pageSize) {
        if(StringUtils.hasText(query.getKeyword())) {
            searchHistoryService.recordHistory(query.getKeyword());
        }

        Page<Prompt> page = new Page<>(pageNo, pageSize);
        if(StringUtils.hasText(query.getKeyword())) {
            return searchPublicWithFullText(query, pageNo, pageSize);
        }
        LambdaQueryWrapper<Prompt> wrapper = buildPublicQueryWrapper(query);
        Page<Prompt> promptPage = promptMapper.selectPage(page, wrapper);

        List<PromptVO> voList = fillFavoriteAndLikeStatus(promptPage.getRecords());

        return PageResult.of(
                promptPage.getCurrent(),
                promptPage.getSize(),
                promptPage.getTotal(),
                voList
        );
    }

    @Override
    public PageResult<PromptVO> queryMyPage(PromptQueryDTO query, Integer pageNo, Integer pageSize) {
        Long userId = requireCurrentUserId();
        Page<Prompt> page = new Page<>(pageNo, pageSize);

        if(StringUtils.hasText(query.getKeyword())) {
            return searchWithMyFullText(query, query.getUserId(),pageNo, pageSize);
        }

        LambdaQueryWrapper<Prompt> wrapper = buildMyQueryWrapper(query, userId);

        Page<Prompt> promptPage = promptMapper.selectPage(page, wrapper);
        List<PromptVO> voList = fillFavoriteAndLikeStatus(promptPage.getRecords());
        return PageResult.of(
                promptPage.getCurrent(),
                promptPage.getSize(),
                promptPage.getTotal(),
                voList
        );
    }

    public List<PromptVO> getHotList(String type, int limit) {
        LambdaQueryWrapper<Prompt> wrapper = new LambdaQueryWrapper<>();
        switch (type) {
            case "like":
                wrapper.orderByDesc(Prompt::getLikeCount);
                break;
            case "favorite":
                wrapper.orderByDesc(Prompt::getFavoriteCount);
                break;
            case "view":
                wrapper.orderByDesc(Prompt::getViewCount);
                break;
            case "copy":
                wrapper.orderByDesc(Prompt::getCopyCount);
                break;
            default:
                wrapper.orderByDesc(Prompt::getUpdateTime);
        }
        wrapper.last("limit " + limit);
        List<Prompt> prompts = promptMapper.selectList(wrapper);
        return promptConverter.toVOList(prompts);

    }

    public String copyPrompt(Long id) {
        Prompt prompt = promptMapper.selectById(id);
        if (prompt == null) {
            throw new BusinessException(404, "prompt not exist");
        }
        Long currentUserId = requireCurrentUserId();
        boolean owner = currentUserId != null && currentUserId.equals(prompt.getUserId());
        if (!owner) {
            if (prompt.getStatus() != PromptStatus.ENABLED) {
                throw new BusinessException(404, "prompt not exist");
            }
            if (prompt.getVisibility() != PromptVisibility.PUBLIC) {
                throw new BusinessException(404, "prompt not exist");
            }
        }
        redisCacheService.incrementCopyCount(id);

        log.info("copy prompt success: promptId:{}, copyCount:{}", id,  prompt.getCopyCount());
        return prompt.getContent();

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
        List<PromptVO> voList = fillFavoriteAndLikeStatus(promptPage.getRecords());
        return PageResult.of(
                promptPage.getCurrent(),
                promptPage.getSize(),
                promptPage.getTotal(),
                voList
        );
    }
    //公共搜索
    private PageResult<PromptVO> searchPublicWithFullText(PromptQueryDTO query, int pageNo, int pageSize) {
        Page<Prompt> page = new Page<>(pageNo, pageSize);
        Page<Prompt> promptPage = promptMapper.searchPublicFullText(page, query.getKeyword(), query.getVisibility(), query.getStatus());
        List<PromptVO> voList = fillFavoriteAndLikeStatus(promptPage.getRecords());
        return PageResult.of(
                promptPage.getCurrent(),
                promptPage.getSize(),
                promptPage.getTotal(),
                voList
        );
    }
    //辅助收藏和点赞分页逻辑
    private List<PromptVO> fillFavoriteAndLikeStatus(List<Prompt> prompts) {
        List<PromptVO> voList = promptConverter.toVOList(prompts);
        Long userId = UserContext.getUserId();
        if (userId == null) {
            voList.forEach(v -> v.setIsFavorite(false));
            voList.forEach(v -> v.setIsLike(false));
            return voList;
        }

        List<Long> promptIds = prompts.stream()
                .map(Prompt::getId)
                .collect(Collectors.toList());
        if (promptIds.isEmpty()) {
            voList.forEach(v -> v.setIsFavorite(false));
            voList.forEach(v -> v.setIsLike(false));
            return voList;
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
        return voList;
    }

    //辅助用于获取当前用户ID，检查登陆状态
    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "not logged in, please log in first");
        }
        return userId;
    }
    //校验分类状态
    private void validateCategoryExists(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("category not exist");
        }
    }
}
