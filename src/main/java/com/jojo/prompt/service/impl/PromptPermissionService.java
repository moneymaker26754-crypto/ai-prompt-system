package com.jojo.prompt.service.impl;

import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.mapper.CategoryMapper;
import com.jojo.prompt.mapper.PromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor

public class PromptPermissionService {

    private final PromptMapper promptMapper;
    private final CategoryMapper categoryMapper;

    //辅助用于获取当前用户ID，检查登陆状态
    public Long requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(401, "not logged in, please log in first");
        }
        Object principal = authentication.getPrincipal();
        if(principal instanceof Long userId) {
            return userId;
        }
        throw new BusinessException(401, "not logged in, please log in first");
    }
    //提示词，用户状态和可见性检查
    public Prompt validatePromptExists(Long promptId, Long userId) {
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
    //校验分类状态
    public void validateCategoryExists(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("category not exist");
        }
    }
}
