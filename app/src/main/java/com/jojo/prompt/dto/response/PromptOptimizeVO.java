package com.jojo.prompt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "提示词优化结果")
public class PromptOptimizeVO {

    @Schema(description = "优化记录编号")
    private Long recordId;

    @Schema(description = "原始提示词")
    private String originalPrompt;

    @Schema(description = "提示词分析结果")
    private String analysisResult;

    @Schema(description = "优化后提示词")
    private String optimizedPrompt;

    @Schema(description = "提示词评审结果")
    private String reviewResult;

    @Schema(description = "评审分数")
    private Integer score;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "评审步骤列表")
    private List<ReviewStepVO> reviewReport;
}
