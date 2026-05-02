package com.jojo.prompt.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.handler.optimization.PromptOptimizeReviewContext;
import com.jojo.prompt.common.handler.optimization.PromptOptimizeReviewHandler;
import com.jojo.prompt.converter.PromptOptimizationConverter;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.PromptOptimizeVO;
import com.jojo.prompt.entity.PromptOptimizationRecord;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.mapper.PromptOptimizationRecordMapper;
import com.jojo.prompt.mapper.PromptTemplateMapper;
import com.jojo.prompt.service.PromptOptimizationService;
import com.jojo.prompt.service.agent.PromptAnalyzeAgent;
import com.jojo.prompt.service.agent.PromptOptimizeAgent;
import com.jojo.prompt.service.agent.PromptReviewAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptOptimizationServiceImpl implements PromptOptimizationService {

    private static final Pattern SCORE_PATTERN = Pattern.compile("Score\\s*:\\s*(\\d{1,3})",
            Pattern.CASE_INSENSITIVE);

    private final PromptTemplateMapper promptTemplateMapper;
    private final PromptOptimizationRecordMapper promptOptimizationRecordMapper;
    private final PromptPermissionService promptPermissionService;
    private final PromptOptimizeReviewHandler promptOptimizeReviewChain;
    private final PromptAnalyzeAgent promptAnalyzeAgent;
    private final PromptOptimizeAgent promptOptimizeAgent;
    private final PromptReviewAgent promptReviewAgent;
    private final PromptOptimizationConverter promptOptimizationConverter;
    private final ObjectMapper objectMapper;

    @Qualifier("promptOptimizeOllamaChatOptions")
    private final OllamaChatOptions promptOptimizeOllamaChatOptions;

    @Override
    public PromptOptimizeVO optimize(PromptOptimizeRequestDTO dto) {
        Long userId = promptPermissionService.requireCurrentUserId();
        PromptTemplate template = promptTemplateMapper.selectById(dto.getTemplateId());
        if (template == null) {
            throw new BusinessException("template not exist");
        }

        //添加需要优化的提示词
        PromptOptimizeReviewContext context = new PromptOptimizeReviewContext();
        context.setUserId(userId);
        context.setTemplate(template);
        context.setRequest(dto);
        //执行审核链
        promptOptimizeReviewChain.review(context);

        //记录优化的提示词以及相关参数
        PromptOptimizationRecord record = new PromptOptimizationRecord();
        record.setUserId(userId);
        record.setTemplateId(template.getId());
        record.setOriginalPrompt(dto.getOriginalPrompt());
        record.setModelName(promptOptimizeOllamaChatOptions.getModel());

        try {
            //agent优化工作流
            log.info("prompt optimize start, templateId={}, userId={}", dto.getTemplateId(), userId);

            log.info("prompt analyze start");
            String analysisResult = promptAnalyzeAgent.analyze(dto.getOriginalPrompt(), template);
            log.info("prompt analyze done");

            log.info("prompt optimize start");
            String optimizedPrompt = promptOptimizeAgent.optimize(dto, template, analysisResult);
            log.info("prompt optimize done");

            log.info("prompt review start");
            String reviewResult = promptReviewAgent.review(dto.getOriginalPrompt(), optimizedPrompt);
            log.info("prompt review done");

            record.setAnalysisResult(analysisResult);
            record.setOptimizedPrompt(optimizedPrompt);
            record.setReviewResult(reviewResult);
            record.setScore(extractScore(reviewResult));
            record.setRiskLevel(extractRiskLevel(reviewResult));
            record.setReviewReport(writeReviewReport(context));
            record.setStatus("SUCCESS");
            promptOptimizationRecordMapper.insert(record);

            log.info("prompt optimize success: recordId={}, templateId={}, userId={}",
                    record.getId(), template.getId(), userId);
            return promptOptimizationConverter.toVO(record, context.getSteps());
        } catch (Exception ex) {
            record.setStatus("FAILED");
            record.setErrorMessage(ex.getMessage());
            record.setReviewReport(writeReviewReportQuietly(context));
            promptOptimizationRecordMapper.insert(record);
            log.error("prompt optimize failed: templateId={}, userId={}", template.getId(), userId, ex);
            throw new BusinessException("prompt optimize failed: " + ex.getMessage());
        }
    }

    @Override
    public PromptOptimizeVO getById(Long id) {
        Long userId = promptPermissionService.requireCurrentUserId();
        PromptOptimizationRecord record = promptOptimizationRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("prompt optimize record not exist");
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(403, "no permission to view this prompt optimize record");
        }
        return promptOptimizationConverter.toVO(record);
    }

    private Integer extractScore(String review) {
        Matcher matcher = SCORE_PATTERN.matcher(review == null ? "" : review);
        if (matcher.find()) {
            return Math.min(100, Integer.parseInt(matcher.group(1)));
        }
        return 0;
    }

    private String extractRiskLevel(String review) {
        if (!StringUtils.hasText(review)) {
            return "LOW";
        }
        if (review.contains("HIGH")) {
            return "HIGH";
        }
        if (review.contains("MEDIUM")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String writeReviewReport(PromptOptimizeReviewContext context) throws JsonProcessingException {
        return objectMapper.writeValueAsString(context.getSteps());
    }

    private String writeReviewReportQuietly(PromptOptimizeReviewContext context) {
        try {
            return objectMapper.writeValueAsString(context.getSteps());
        } catch (JsonProcessingException ex) {
            log.warn("write prompt optimize review report failed", ex);
            return null;
        }
    }
}
