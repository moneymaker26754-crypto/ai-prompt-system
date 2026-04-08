package com.jojo.prompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("prompt")
public class Prompt {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private Long categoryId;

    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private String tags;

    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private PromptVisibility visibility;

    private PromptStatus status;

    private Integer viewCount;

    private Integer copyCount;

    private Long userId;

    //收藏数量
    private Integer favoriteCount;

    //喜欢数量
    private Integer likeCount;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
