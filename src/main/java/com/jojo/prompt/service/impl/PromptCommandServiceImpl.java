package com.jojo.prompt.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.dto.request.PromptCreateDTO;
import com.jojo.prompt.dto.request.PromptUpdateDTO;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.mapper.CategoryMapper;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.PromptCommandService;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptCommandServiceImpl implements PromptCommandService {

    private final PromptMapper promptMapper;
    private final RedisCacheService redisCacheService;
    private  final CategoryMapper categoryMapper;
    private final PromptPermissionService promptPermissionService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPrompt(PromptCreateDTO dto) {
        Long userId = promptPermissionService.requireCurrentUserId();
        promptPermissionService.validateCategoryExists(dto.getCategoryId());

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

        Long userId = promptPermissionService.requireCurrentUserId();
        if (!userId.equals(existing.getUserId())) {
            throw new BusinessException(403, "no permission to modify this prompt");
        }
        promptPermissionService.validateCategoryExists(dto.getCategoryId());

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

        Long userId = promptPermissionService.requireCurrentUserId();
        if (!userId.equals(prompt.getUserId())) {
            throw new BusinessException(403, "no permission to delete this prompt");
        }
        promptMapper.deleteById(id);
        //删除缓存
        redisCacheService.deletePromptCache(id);
    }

    public String copyPrompt(Long id) {
        Prompt prompt = promptMapper.selectById(id);
        if (prompt == null) {
            throw new BusinessException(404, "prompt not exist");
        }
        Long currentUserId = promptPermissionService.requireCurrentUserId();
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

        log.info("copy prompt success: promptId:{}, copyCount:{}", id, prompt.getCopyCount());
        return prompt.getContent();

    }

}
