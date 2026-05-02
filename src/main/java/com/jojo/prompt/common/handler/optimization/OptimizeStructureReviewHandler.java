package com.jojo.prompt.common.handler.optimization;

import com.jojo.prompt.dto.response.ReviewStepVO;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OptimizeStructureReviewHandler extends AbstractPromptOptimizeReviewHandler {

    @Override
    protected void doReview(PromptOptimizeReviewContext context) {
        String prompt = context.getRequest().getOriginalPrompt().toLowerCase(Locale.ROOT);
        boolean weak = !prompt.contains("task")
                && !prompt.contains("requirement")
                && !prompt.contains("output")
                && !prompt.contains("任务")
                && !prompt.contains("要求")
                && !prompt.contains("输出");
        if (weak) {
            context.getSteps().add(new ReviewStepVO(getNodeName(), "WARN", "prompt structure is weak"));
            return;
        }
        context.getSteps().add(new ReviewStepVO(getNodeName(), "PASS", "structure check passed"));
    }

    @Override
    protected String getNodeName() {
        return "OptimizeStructureReviewHandler";
    }
}
