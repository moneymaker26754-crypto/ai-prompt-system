package com.jojo.prompt.common.handler.optimization;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.dto.response.ReviewStepVO;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class OptimizeSensitiveWordReviewHandler extends AbstractPromptOptimizeReviewHandler {

    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "violence",
            "porn",
            "terrorism",
            "暴力",
            "色情",
            "恐怖主义"
    );

    @Override
    protected void doReview(PromptOptimizeReviewContext context) {
        String prompt = context.getRequest().getOriginalPrompt();
        String lowerPrompt = prompt.toLowerCase(Locale.ROOT);
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerPrompt.contains(sensitiveWord.toLowerCase(Locale.ROOT))) {
                throw new BusinessException("original prompt contains sensitive word");
            }
        }
        context.getSteps().add(new ReviewStepVO(getNodeName(), "PASS", "sensitive word check passed"));
    }

    @Override
    protected String getNodeName() {
        return "OptimizeSensitiveWordReviewHandler";
    }
}
