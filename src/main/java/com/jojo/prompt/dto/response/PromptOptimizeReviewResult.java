package com.jojo.prompt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "提示词优化评审结果")
public class PromptOptimizeReviewResult {

    @Schema(description = "评审分数（0-100）", example = "85")
    private Integer score;

    @Schema(description = "风险等级", example = "LOW")
    private String riskLevel;

    @Schema(description = "是否改变原始意图", example = "false")
    private Boolean changedIntent;

    @Schema(description = "评审说明", example = "优化后表达更清晰，未改变原始意图")
    private String reviewComment;
}
