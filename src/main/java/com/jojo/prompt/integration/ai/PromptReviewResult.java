package com.jojo.prompt.integration.ai;

public record PromptReviewResult(
        Integer score,
        String riskLevel,
        Boolean changedIntent,
        String reviewComment,
        String model
) {
}
