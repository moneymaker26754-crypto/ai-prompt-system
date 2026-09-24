package com.jojo.prompt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "提示词模板更新请求参数")
public class PromptTemplateUpdateDTO {

    @NotNull(message = "模板编号不能为空")
    @Schema(description = "模板编号", example = "1")
    private Long id;

    @NotBlank(message = "模板名称不能为空")
    @Length(max = 100, message = "模板名称长度不能超过100")
    @Schema(description = "模板名称", example = "通用优化模板")
    private String name;

    @NotBlank(message = "应用场景不能为空")
    @Length(max = 100, message = "应用场景长度不能超过100")
    @Schema(description = "应用场景", example = "通用场景")
    private String scene;

    @Length(max = 500, message = "模板描述长度不能超过500")
    @Schema(description = "模板描述", example = "适用于常见提示词优化场景")
    private String description;

    @NotBlank(message = "系统提示词不能为空")
    @Schema(description = "系统提示词")
    private String systemPrompt;

    @NotBlank(message = "优化指令不能为空")
    @Schema(description = "优化指令")
    private String optimizeInstruction;

    @Schema(description = "启用状态", example = "1")
    private Integer enabled;
}
