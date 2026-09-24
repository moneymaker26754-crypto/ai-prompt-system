package com.jojo.prompt.dto.response;

import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "提示词返回对象")
public class PromptVO {

    @Schema(description = "提示词ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;  //关联查询分类名称

    @Schema(description = "标签列表")
    private List<String> tagList;  //将逗号分隔的字符串转为列表

    @Schema(description = "可见性")
    private PromptVisibility visibility;

    @Schema(description = "状态")
    private PromptStatus status;

    @Schema(description = "查看次数")
    private Integer viewCount;

    @Schema(description = "复制次数")
    private Integer copyCount;

    @Schema(description = "版本号", example = "1")
    private Integer version;

    @Schema(description = "当前用户是否已收藏")//1.5新增
    private Boolean isFavorite;

    @Schema(description = "当前用户是否已点赞")//1.5新增
    private Boolean isLike;

    @Schema(description = "收藏数")//1.5新增
    private Integer favoriteCount;

    @Schema(description = "点赞数")//1.5新增
    private Integer likeCount;


    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
