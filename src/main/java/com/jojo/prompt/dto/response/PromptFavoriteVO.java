package com.jojo.prompt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "收藏的提示词")
public class PromptFavoriteVO {

    @Schema(description = "收藏ID")
    private Long favoriteId;

    @Schema(description = "提示词信息")
    private PromptVO promptVO;

    @Schema(description = "收藏时间")
    private LocalDateTime favoriteTime;
}
