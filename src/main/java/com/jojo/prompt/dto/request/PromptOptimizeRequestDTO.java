package com.jojo.prompt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "提示词优化请求参数")
public class PromptOptimizeRequestDTO {

    @NotNull(message = "模板编号不能为空")
    @Schema(description = "模板编号", example = "1")
    private Long templateId;

    @NotBlank(message = "原始提示词不能为空")
    @Length(max = 5000, message = "原始提示词长度不能超过5000")
    @Schema(description = "原始提示词", example = "请为这款产品生成一段简洁的介绍文案")
    private String originalPrompt;

    @Length(max = 500, message = "优化目标长度不能超过500")
    @Schema(description = "优化目标", example = "提升表达清晰度，并明确输出结构")
    private String target;

    @Length(max = 500, message = "期望输出格式长度不能超过500")
    @Schema(description = "期望输出格式", example = "列表形式输出")
    private String outputFormat;
}
