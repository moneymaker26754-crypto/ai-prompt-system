package com.jojo.prompt.common.handler.optimization;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.dto.response.ReviewStepVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OptimizeBasicReviewHandler extends AbstractPromptOptimizeReviewHandler {

    @Override
    protected void doReview(PromptOptimizeReviewContext context) {
        String prompt = context.getRequest().getOriginalPrompt();
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("original prompt is empty");
        }
        if (context.getTemplate() == null || !Integer.valueOf(1).equals(context.getTemplate().getEnabled())) {
            throw new BusinessException("template not available");
        }
        context.getSteps().add(new ReviewStepVO(getNodeName(), "PASS", "basic check passed"));
    }

    @Override
    protected String getNodeName() {
        return "OptimizeBasicReviewHandler";
    }
}
