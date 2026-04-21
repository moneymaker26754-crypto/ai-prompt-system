package com.jojo.prompt.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptFavoriteEvent {

    private Long promptId;

    //点赞用户ID
    private Long userId;

    //作者ID
    private Long authorId;

    private LocalDateTime favoriteTime;
}
