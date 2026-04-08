package com.jojo.prompt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "创建分类请求参数")
public class CategoryCreateDTO {

    @NotBlank(message = "分类名称不能为空")
    @Length(max = 50, message = "分类名称长度不能超过50")
    @Schema(description = "分类名称", example = "编程开发")
    private String name;

    @Length(max = 200, message = "分类描述长度不能超过200")
    @Schema(description = "分类描述", example = "AI辅助编程相关的提示词")
    private String description;

    @Schema(description = "排序（数字越小越靠前）", example = "1")
    private Integer sortOrder = 0;
}