package com.jojo.prompt.dto.request;

import com.jojo.prompt.common.constant.PromptVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "确认保存优化结果请求参数")
public class PromptOptimizeConfirmDTO {

    @NotNull
    @Schema(description = "优化记录编号", example = "1")
    private Long recordId;

    @NotBlank
    @Schema(description = "保存后的提示词标题", example = "电商商品介绍文案优化版")
    private String title;

    @NotNull
    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "标签（逗号分隔）", example = "电商,文案优化")
    private String tags;

    @Schema(description = "可见性", example = "public")
    private PromptVisibility visibility = PromptVisibility.PUBLIC;
}
