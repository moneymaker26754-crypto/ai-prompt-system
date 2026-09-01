package com.jojo.prompt.common.handler;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.entity.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
//质量检测处理器
public class QualityReviewHandler extends AbstractReviewHandler {

    private static final int MIN_TITLE_LENGTH = 5;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MIN_CONTENT_LENGTH = 20;
    private static final int MAX_CONTENT_LENGTH = 5000;

    @Override
    protected void doReview(Prompt prompt) {
        String title = prompt.getTitle();
        String content = prompt.getContent();

        if(!StringUtils.hasText(title) ||  !StringUtils.hasText(content)) {
            log.warn("quality review failed, promptId={}, status=empty_content", prompt.getId());
            throw new BusinessException("title or content is null");
        }

        //检测标题长度
        if(title.length() < MIN_TITLE_LENGTH || title.length() > MAX_TITLE_LENGTH) {
            log.warn("quality review failed, promptId={}, status=invalid_title_length, titleLength={}", prompt.getId(), title.length());
            throw new BusinessException("title length should be between " + MIN_TITLE_LENGTH + " and " + MAX_TITLE_LENGTH);
        }
        //检测内容长度
        if(content.length() < MIN_CONTENT_LENGTH || content.length() > MAX_CONTENT_LENGTH) {
            log.warn("quality review failed, promptId={}, status=invalid_content_length, contentLength={}", prompt.getId(), content.length());
            throw new BusinessException("content length should be between " + MIN_CONTENT_LENGTH + " and " + MAX_CONTENT_LENGTH);
        }
        //检测内容结构
        if(!containsBasicElement(content)) {
            log.warn("quality review failed, promptId={}, status=missing_basic_elements", prompt.getId());
            throw new BusinessException("prompt content should contain basic elements like question, answer, instruction, etc.");
        }

        log.info("quality review completed, promptId={}, status=success", prompt.getId());
    }

    private boolean containsBasicElement(String content) {
        //简单规则：至少包含角色、任务、要求等关键词中的一个，实际项目可以使用更复杂的NLP方法检测
        String[] keyWords = {"角色", "任务", "要求", "问题", "回答", "指令", "请", "帮我", "怎么做", "为什么"};
        for(String word : keyWords) {
            if(content.contains(word)) {
              return true;
            }
        }
        return false;
    }

    @Override
    protected String getHandlerName() {
        return "QualityReviewHandler";
    }
}
