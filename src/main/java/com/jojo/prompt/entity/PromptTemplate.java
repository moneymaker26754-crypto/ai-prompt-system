package com.jojo.prompt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_template")
@Schema(description = "提示词模板实体")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    @Schema(description = "模板编号")
    private Long id;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "应用场景")
    private String scene;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "优化指令")
    private String optimizeInstruction;

    @Schema(description = "启用状态")
    private Integer enabled;

    @Schema(description = "内置状态")
    private Integer builtIn;

    @Schema(description = "所属用户编号")
    private Long userId;

    @TableLogic
    @Schema(description = "删除标记")
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
