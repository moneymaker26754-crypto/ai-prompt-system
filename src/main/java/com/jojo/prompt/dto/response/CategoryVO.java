package com.jojo.prompt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "分类返回对象")
public class CategoryVO {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "分类描述")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "该分类下的提示词数量")
    private Long promptCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
