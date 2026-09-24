package com.jojo.prompt.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.dto.response.PromptOptimizeVO;
import com.jojo.prompt.dto.response.ReviewStepVO;
import com.jojo.prompt.entity.PromptOptimizationRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Component
public class PromptOptimizationConverter {

    private static final TypeReference<List<ReviewStepVO>> REVIEW_STEP_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public PromptOptimizationConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PromptOptimizeVO toVO(PromptOptimizationRecord record, List<ReviewStepVO> steps) {
        if (record == null) {
            return null;
        }
        PromptOptimizeVO vo = buildBaseVO(record);
        vo.setReviewReport(steps == null ? Collections.emptyList() : steps);
        return vo;
    }

    public PromptOptimizeVO toVO(PromptOptimizationRecord record) {
        if (record == null) {
            return null;
        }
        PromptOptimizeVO vo = buildBaseVO(record);
        vo.setReviewReport(parseReviewReport(record.getReviewReport()));
        return vo;
    }

    private PromptOptimizeVO buildBaseVO(PromptOptimizationRecord record) {
        PromptOptimizeVO vo = new PromptOptimizeVO();
        vo.setRecordId(record.getId());
        vo.setOriginalPrompt(record.getOriginalPrompt());
        vo.setAnalysisResult(record.getAnalysisResult());
        vo.setOptimizedPrompt(record.getOptimizedPrompt());
        vo.setReviewResult(record.getReviewResult());
        vo.setScore(record.getScore());
        vo.setRiskLevel(record.getRiskLevel());
        return vo;
    }

    private List<ReviewStepVO> parseReviewReport(String reviewReport) {
        if (!StringUtils.hasText(reviewReport)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(reviewReport, REVIEW_STEP_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("prompt optimize review report invalid");
        }
    }
}
