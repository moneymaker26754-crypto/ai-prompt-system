package com.jojo.prompt.common.mq.consumer;

import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.handler.PromptReviewHandler;
import com.jojo.prompt.common.mq.message.PromptReviewMessage;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptReviewConsumerTest {

    @Mock
    private PromptMapper promptMapper;

    @Mock
    private PromptReviewHandler reviewChain;

    @Mock
    private RedisCacheService redisCacheService;

    @InjectMocks
    private PromptReviewConsumer consumer;

    @Test
    void consumeShouldRejectBusinessExceptionWithoutRetry() {
        Prompt prompt = reviewingPrompt(100L, 3);
        PromptReviewMessage message = new PromptReviewMessage(
                100L,
                3,
                "CREATE",
                10L,
                LocalDateTime.of(2026, 4, 16, 10, 0)
        );
        when(promptMapper.selectById(100L)).thenReturn(prompt);
        doThrow(new BusinessException("sensitive content")).when(reviewChain).review(prompt);

        assertDoesNotThrow(() -> consumer.consume(message));

        verify(promptMapper).updateStatusByIdAndVersion(100L, 3, PromptStatus.REVIEWING, PromptStatus.REJECTED);
        verify(redisCacheService).deletePromptCache(100L);
    }

    @Test
    void consumeShouldRethrowUnexpectedExceptionForRetry() {
        Prompt prompt = reviewingPrompt(101L, 4);
        PromptReviewMessage message = new PromptReviewMessage(
                101L,
                4,
                "UPDATE",
                11L,
                LocalDateTime.of(2026, 4, 16, 10, 1)
        );
        when(promptMapper.selectById(101L)).thenReturn(prompt);
        doThrow(new IllegalStateException("review service timeout")).when(reviewChain).review(prompt);

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeout");

        verify(promptMapper, never()).updateStatusByIdAndVersion(101L, 4, PromptStatus.REVIEWING, PromptStatus.REJECTED);
        verify(redisCacheService, never()).deletePromptCache(101L);
    }

    private Prompt reviewingPrompt(Long promptId, Integer version) {
        Prompt prompt = new Prompt();
        prompt.setId(promptId);
        prompt.setVersion(version);
        prompt.setStatus(PromptStatus.REVIEWING);
        return prompt;
    }
}
