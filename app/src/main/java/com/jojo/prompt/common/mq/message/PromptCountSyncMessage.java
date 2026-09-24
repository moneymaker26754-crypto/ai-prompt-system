package com.jojo.prompt.common.mq.message;

import java.io.Serializable;

public record PromptCountSyncMessage(Long promptId) implements Serializable {
}
