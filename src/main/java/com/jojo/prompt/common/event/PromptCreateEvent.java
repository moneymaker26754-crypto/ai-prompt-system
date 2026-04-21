package com.jojo.prompt.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptCreateEvent {

    private Long promptId;

    //作者ID
    private Long userId;

    private String title;

    private LocalDateTime createTime;
}
