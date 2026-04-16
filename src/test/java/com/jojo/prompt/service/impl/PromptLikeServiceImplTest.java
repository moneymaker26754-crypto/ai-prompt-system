package com.jojo.prompt.service.impl;

import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.common.event.PromptHeatEvent;
import com.jojo.prompt.common.event.PromptLikeEvent;
import com.jojo.prompt.converter.PromptConverter;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.entity.PromptLike;
import com.jojo.prompt.mapper.CategoryMapper;
import com.jojo.prompt.mapper.PromptLikeMapper;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.RedisCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptLikeServiceImplTest {

    @Mock
    private PromptLikeMapper promptLikeMapper;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private PromptMapper promptMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CategoryMapper categoryMapper;

    private PromptLikeServiceImpl promptLikeService;

    @BeforeEach
    void setUp() {
        PromptPermissionService promptPermissionService = new PromptPermissionService(
                promptMapper,
                categoryMapper,
                redisCacheService,
                new PromptConverter()
        );
        promptLikeService = new PromptLikeServiceImpl(
                promptLikeMapper,
                redisCacheService,
                promptPermissionService,
                eventPublisher
        );
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(10L, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void likePromptShouldPublishHeatAndLikeEvents() {
        Prompt prompt = new Prompt();
        prompt.setId(100L);
        prompt.setUserId(20L);
        prompt.setStatus(PromptStatus.ENABLED);
        prompt.setVisibility(PromptVisibility.PUBLIC);

        when(redisCacheService.isUserLiked(anyLong(), anyLong())).thenReturn(false);
        when(promptMapper.selectById(100L)).thenReturn(prompt);
        when(promptLikeMapper.selectCount(any())).thenReturn(0L);
        when(promptLikeMapper.insert(any(PromptLike.class))).thenReturn(1);

        promptLikeService.likePrompt(100L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        List<Object> events = eventCaptor.getAllValues();
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(PromptHeatEvent.class);
            PromptHeatEvent heatEvent = (PromptHeatEvent) event;
            assertThat(heatEvent.getPromptId()).isEqualTo(100L);
            assertThat(heatEvent.getUserId()).isEqualTo(10L);
            assertThat(heatEvent.getAction()).isEqualTo("like");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(PromptLikeEvent.class);
            PromptLikeEvent likeEvent = (PromptLikeEvent) event;
            assertThat(likeEvent.getPromptId()).isEqualTo(100L);
            assertThat(likeEvent.getUserId()).isEqualTo(10L);
            assertThat(likeEvent.getAuthorId()).isEqualTo(20L);
        });
    }
}
