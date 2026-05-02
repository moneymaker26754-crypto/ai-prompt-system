package com.jojo.prompt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "提示词优化评审步骤")
public class ReviewStepVO {

    @Schema(description = "评审节点名称")
    private String node;

    @Schema(description = "评审结果")
    private String result;

    @Schema(description = "评审说明")
    private String message;
}
