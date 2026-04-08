package com.jojo.prompt.dto.request;

import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.entity.Prompt;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "创建提示词请求参数")
public class PromptCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Length(max = 100, message = "标题长度不能超过100")
    @Schema(description = "提示词标题", example = "代码注释生成器")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Length(max = 5000, message = "内容长度不能超过5000")
    @Schema(description = "提示词内容")
    private String content;

    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "标签(逗号分隔)", example = "编程,注释")
    private String tags;

    @Schema(description = "可见性", example = "public")
    private PromptVisibility visibility = PromptVisibility.PUBLIC;

}
