package com.jojo.prompt.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptHeatEvent {

    private Long promptId;

    private Long userId;

    private String action;

    private LocalDateTime eventTime;
}
