package com.jojo.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.result.PageResult;
import com.jojo.prompt.common.utils.UserContext;
import com.jojo.prompt.converter.PromptConverter;
import com.jojo.prompt.dto.response.PromptFavoriteVO;
import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Category;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.entity.PromptFavorite;
import com.jojo.prompt.mapper.CategoryMapper;
import com.jojo.prompt.mapper.PromptFavoriteMapper;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.PromptFavoriteService;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptFavoriteServiceImpl implements PromptFavoriteService {

    private final PromptFavoriteMapper promptFavoriteMapper;
    private final PromptMapper promptMapper;
    private final CategoryMapper categoryMapper;
    private final PromptConverter promptConverter;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoritePrompt(Long id) {
        //获取用户id
        Long userId = requireCurrentUserId();
        //提示词是否存在
        Prompt prompt = validatePromptExists(id, userId);
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavoritePrompt(Long id) {
        Long userId = requireCurrentUserId();
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

    }


    @Override
    public PageResult<PromptFavoriteVO> queryMyFavoritePrompt(int pageNo, int pageSize) {
        Long userId = requireCurrentUserId();
        //分页查询收藏记录
        Page<PromptFavorite> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PromptFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptFavorite::getUserId, userId)
                .orderByDesc(PromptFavorite::getCreateTime);//按时间倒序
        Page<PromptFavorite> favoritePage = promptFavoriteMapper.selectPage(page, wrapper);
        //查询提示词详细
        List<PromptFavoriteVO> voList = favoritePage.getRecords().stream()
                .map(favorite -> {
                    //查询提示词
                    Prompt prompt = promptMapper.selectById(favorite.getPromptId());
                    if(prompt == null) {
                        return null;//提示词已被删除
                    }
                    //查询分类
                    Category category = categoryMapper.selectById(prompt.getCategoryId());
                    //转换为VO
                    PromptVO promptVO = promptConverter.toVO(prompt, category);
                    promptVO.setIsFavorite(true);//都是已收藏的
                    //组装收藏VO
                    PromptFavoriteVO vo = new PromptFavoriteVO();
                    vo.setFavoriteId(favorite.getId());
                    vo.setPromptVO(promptVO);
                    vo.setFavoriteTime(favorite.getCreateTime());
                    return vo;
                })
                .filter(vo -> vo != null)//过滤掉已删除的提示词
                .collect(Collectors.toList());

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

    //权限检查，查看是否登录
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
            throw new BusinessException(403, "no permission to favorite this prompt");
        }
        if (userId.equals(prompt.getUserId())) {
            throw new BusinessException(400, "cannot favorite your own prompt");
        }
        return prompt;
    }
}
