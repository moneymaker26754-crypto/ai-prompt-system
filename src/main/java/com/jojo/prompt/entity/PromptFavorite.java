package com.jojo.prompt.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_favorite")
public class PromptFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long promptId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
