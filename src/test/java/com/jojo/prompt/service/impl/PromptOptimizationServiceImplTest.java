package com.jojo.prompt.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.handler.optimization.PromptOptimizeReviewHandler;
import com.jojo.prompt.converter.PromptOptimizationConverter;
import com.jojo.prompt.dto.response.PromptOptimizeVO;
import com.jojo.prompt.entity.PromptOptimizationRecord;
import com.jojo.prompt.integration.ai.PromptAiGateway;
import com.jojo.prompt.mapper.PromptOptimizationRecordMapper;
import com.jojo.prompt.mapper.PromptTemplateMapper;
import com.jojo.prompt.service.PromptCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptOptimizationServiceImplTest {

    @Mock
    private PromptTemplateMapper promptTemplateMapper;

    @Mock
    private PromptOptimizationRecordMapper promptOptimizationRecordMapper;

    @Mock
    private PromptPermissionService promptPermissionService;

    @Mock
    private PromptOptimizeReviewHandler promptOptimizeReviewHandler;


    @Mock
    private PromptAiGateway promptAiGateway;

    @Mock
    private PromptCommandService promptCommandService;

    private PromptOptimizationServiceImpl promptOptimizationService;


    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        PromptOptimizationConverter promptOptimizationConverter = new PromptOptimizationConverter(objectMapper);
        promptOptimizationService = new PromptOptimizationServiceImpl(
                promptTemplateMapper,
                promptOptimizationRecordMapper,
                promptPermissionService,
                promptOptimizeReviewHandler,
                promptAiGateway,
                promptOptimizationConverter,
                objectMapper,
                promptCommandService
        );
    }

    @Test
    void getByIdShouldReturnRecordForCurrentUser() {
        PromptOptimizationRecord record = new PromptOptimizationRecord();
        record.setId(1L);
        record.setUserId(10L);
        record.setOriginalPrompt("original");
        record.setAnalysisResult("analysis");
        record.setOptimizedPrompt("optimized");
        record.setReviewResult("Score: 88\nRisk Level: LOW\nReview: ok");
        record.setScore(88);
        record.setRiskLevel("LOW");
        record.setReviewReport("[{\"node\":\"basic\",\"result\":\"PASS\",\"message\":\"ok\"}]");

        when(promptPermissionService.requireCurrentUserId()).thenReturn(10L);
        when(promptOptimizationRecordMapper.selectById(1L)).thenReturn(record);
        PromptOptimizeVO result = promptOptimizationService.getById(1L);

        assertThat(result.getRecordId()).isEqualTo(1L);
        assertThat(result.getOriginalPrompt()).isEqualTo("original");
        assertThat(result.getReviewReport()).hasSize(1);
        assertThat(result.getReviewReport().getFirst().getNode()).isEqualTo("basic");
    }

    @Test
    void getByIdShouldThrowWhenRecordBelongsToAnotherUser() {
        PromptOptimizationRecord record = new PromptOptimizationRecord();
        record.setId(1L);
        record.setUserId(20L);

        when(promptPermissionService.requireCurrentUserId()).thenReturn(10L);
        when(promptOptimizationRecordMapper.selectById(1L)).thenReturn(record);

        assertThatThrownBy(() -> promptOptimizationService.getById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("no permission to view this prompt optimize record");
    }
}
