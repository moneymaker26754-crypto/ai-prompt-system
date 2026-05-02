package com.jojo.prompt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_optimization_record")
@Schema(description = "提示词优化记录实体")
public class PromptOptimizationRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "记录编号")
    private Long id;

    @Schema(description = "用户编号")
    private Long userId;

    @Schema(description = "模板编号")
    private Long templateId;

    @Schema(description = "原始提示词")
    private String originalPrompt;

    @Schema(description = "提示词分析结果")
    private String analysisResult;

    @Schema(description = "优化后提示词")
    private String optimizedPrompt;

    @Schema(description = "提示词评审结果")
    private String reviewResult;

    @Schema(description = "评审分数")
    private Integer score;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "评审报告序列化内容")
    private String reviewReport;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "优化状态")
    private String status;

    @Schema(description = "错误信息")
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
