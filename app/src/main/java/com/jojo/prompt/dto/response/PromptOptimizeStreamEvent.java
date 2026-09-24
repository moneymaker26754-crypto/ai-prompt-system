package com.jojo.prompt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "提示词优化流式事件")
@AllArgsConstructor
@NoArgsConstructor
public class PromptOptimizeStreamEvent {

    @Schema(description = "流式事件阶段", example = "analysis")
    private String stage;

    @Schema(description = "当前阶段输出内容", example = "正在分析原始提示词结构")
    private String content;
}

