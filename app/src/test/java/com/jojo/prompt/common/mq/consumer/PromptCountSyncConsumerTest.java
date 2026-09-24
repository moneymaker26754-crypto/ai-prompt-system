package com.jojo.prompt.common.mq.consumer;

import com.jojo.prompt.common.mq.message.PromptCountSyncMessage;
import com.jojo.prompt.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptCountSyncConsumerTest {

    @Mock
    private RedisCacheService redisCacheService;

    @InjectMocks
    private PromptCountSyncConsumer consumer;

    @Test
    void consumeShouldClearDirtyFlagAfterSuccessfulSync() {
        when(redisCacheService.syncCountToDb(200L)).thenReturn(true);

        assertDoesNotThrow(() -> consumer.consume(new PromptCountSyncMessage(200L)));

        verify(redisCacheService).removeDirtyPromptId(Set.of(200L));
    }

    @Test
    void consumeShouldSkipWhenLockIsBusy() {
        when(redisCacheService.syncCountToDb(201L)).thenReturn(false);

        assertDoesNotThrow(() -> consumer.consume(new PromptCountSyncMessage(201L)));

        verify(redisCacheService, never()).removeDirtyPromptId(Set.of(201L));
    }

    @Test
    void consumeShouldRethrowUnexpectedExceptionForRetry() {
        when(redisCacheService.syncCountToDb(202L)).thenThrow(new IllegalStateException("redis timeout"));

        assertThatThrownBy(() -> consumer.consume(new PromptCountSyncMessage(202L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeout");

        verify(redisCacheService, never()).removeDirtyPromptId(Set.of(202L));
    }
}
