package com.jojo.prompt.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PromptLikeEvent {

    private Long promptId;

    private Long userId;

    private LocalDateTime likeTime;
}
