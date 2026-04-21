package com.jojo.prompt.common.mq.message;

import java.io.Serializable;
import java.time.LocalDateTime;

public record PromptReviewMessage(Long promptId,
                                  Integer expectedVersion,
                                  String operationType,
                                  Long userId,
                                  LocalDateTime submitTime
) implements Serializable {
}
