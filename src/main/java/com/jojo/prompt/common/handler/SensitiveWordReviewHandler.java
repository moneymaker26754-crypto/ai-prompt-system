package com.jojo.prompt.common.handler;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.entity.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
//敏感词检测
public class SensitiveWordReviewHandler extends AbstractReviewHandler {
    //实际项目应该从数据库或配置文件加载敏感词列表，这里简化为硬编码
    private static final Set<String> SENSITIVE_WORDS = new HashSet<>(Arrays.asList("暴力", "色情", "政治", "反动", "恐怖主义"));

    @Override
    protected void doReview(Prompt prompt) {
        log.info("[{}] start checking: promptId={}", getHandlerName(), prompt.getId());
        String content = prompt.getContent();
        String title = prompt.getTitle();
        if(!StringUtils.hasText(content) || !StringUtils.hasText(title)) {
            throw new BusinessException("prompt content or title is empty");
        }
        //检查内容和标题是否含有敏感词
        for(String word : SENSITIVE_WORDS) {
            if(content.contains(word) || title.contains(word)) {
                log.warn("[{}] check failed: promptId={}", getHandlerName(), prompt.getId());
                throw new BusinessException("prompt content or title contains sensitive words");
            }
        }
        log.info("[{}] check success: promptId={}", getHandlerName(), prompt.getId());
    }

    @Override
    protected String getHandlerName() {
        return "SensitiveWordReviewHandler";
    }
}
