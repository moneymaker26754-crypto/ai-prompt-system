package com.jojo.prompt.dto.request;

import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "提示词查询条件")
public class PromptQueryDTO {

    @Schema(description = "搜索关键词")
    private String keyword;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "可见性")
    private PromptVisibility visibility;

    @Schema(description = "状态")
    private PromptStatus status;

    @Schema(description = "标签")
    private String tag;

    @Schema(description = "用户ID")
    private Long userId;

    @Pattern(
            regexp = "^(createTime|viewCount|copyCount)$",
            message = "排序字段必须为: createTime, viewCount, copyCount"
    )
    @Schema(description = "排序字段(createTime/viewCount/copyCount)")
    private String sortField = "createTime";

    @Pattern(
            regexp = "^(asc|desc)$",
            message = "排序方式必须是asc或desc"
    )
    @Schema(description = "排序方式(asc/desc)")
    private String sortOrder = "desc";
}