package com.jojo.prompt.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.handler.optimization.PromptOptimizeReviewContext;
import com.jojo.prompt.common.handler.optimization.PromptOptimizeReviewHandler;
import com.jojo.prompt.converter.PromptOptimizationConverter;
import com.jojo.prompt.dto.request.PromptCreateDTO;
import com.jojo.prompt.dto.request.PromptOptimizeConfirmDTO;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.PromptOptimizeReviewResult;
import com.jojo.prompt.dto.response.PromptOptimizeVO;
import com.jojo.prompt.entity.PromptOptimizationRecord;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.mapper.PromptOptimizationRecordMapper;
import com.jojo.prompt.mapper.PromptTemplateMapper;
import com.jojo.prompt.service.PromptCommandService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptOptimizationServiceImpl implements PromptOptimizationService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PromptOptimizationRecordMapper recordMapper;
    private final PromptPermissionService promptPermissionService;
    private final PromptOptimizeReviewHandler promptOptimizeReviewChain;
    private final PromptAnalyzeAgent promptAnalyzeAgent;
    private final PromptOptimizeAgent promptOptimizeAgent;
    private final PromptReviewAgent promptReviewAgent;
    private final PromptOptimizationConverter promptOptimizationConverter;
    private final ObjectMapper objectMapper;
    //用于创建优化好厚的Prompt
    private final PromptCommandService promptCommandService;

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
            PromptOptimizeReviewResult review = promptReviewAgent.review(dto.getOriginalPrompt(), optimizedPrompt);
            log.info("prompt review done");

            record.setAnalysisResult(analysisResult);
            record.setOptimizedPrompt(optimizedPrompt);
            record.setReviewResult(review == null ? null : review.getReviewComment());
            record.setScore(extractScore(review));
            record.setRiskLevel(extractRiskLevel(review));
            record.setReviewReport(writeReviewReport(context));
            record.setStatus("SUCCESS");
            recordMapper.insert(record);

            log.info("prompt optimize success: recordId={}, templateId={}, userId={}",
                    record.getId(), template.getId(), userId);
            return promptOptimizationConverter.toVO(record, context.getSteps());
        } catch (Exception ex) {
            record.setStatus("FAILED");
            record.setErrorMessage(ex.getMessage());
            record.setReviewReport(writeReviewReportQuietly(context));
            recordMapper.insert(record);
            log.error("prompt optimize failed: templateId={}, userId={}", template.getId(), userId, ex);
            throw new BusinessException("prompt optimize failed: " + ex.getMessage());
        }
    }

    @Override
    public PromptOptimizeVO getById(Long id) {
        Long userId = promptPermissionService.requireCurrentUserId();
        PromptOptimizationRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("prompt optimize record not exist");
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(403, "no permission to view this prompt optimize record");
        }
        return promptOptimizationConverter.toVO(record);
    }

    @Override
    public Long confirmAsPrompt(PromptOptimizeConfirmDTO dto) {
        Long userId = promptPermissionService.requireCurrentUserId();

        PromptOptimizationRecord record = recordMapper.selectById(dto.getRecordId());
        if (record == null) {
            throw new BusinessException("optimization record not exist");
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(403, "no permission");
        }
        if (!"SUCCESS".equals(record.getStatus())) {
            throw new BusinessException("optimization record not ready");
        }
        if (!StringUtils.hasText(record.getOptimizedPrompt())) {
            throw new BusinessException("optimized prompt is empty");
        }

        PromptCreateDTO createDTO = new PromptCreateDTO();
        createDTO.setTitle(dto.getTitle());
        createDTO.setContent(record.getOptimizedPrompt());
        createDTO.setCategoryId(dto.getCategoryId());
        createDTO.setTags(dto.getTags());
        createDTO.setVisibility(dto.getVisibility());
        return promptCommandService.createPrompt(createDTO);
    }


    private Integer extractScore(PromptOptimizeReviewResult reviewResult) {
        if (reviewResult == null || reviewResult.getScore() == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, reviewResult.getScore()));
    }

    private String extractRiskLevel(PromptOptimizeReviewResult reviewResult) {
        if (reviewResult == null || !StringUtils.hasText(reviewResult.getRiskLevel())) {
            return "LOW";
        }

        String riskLevel = reviewResult.getRiskLevel().toUpperCase();
        if (riskLevel.contains("HIGH")) {
            return "HIGH";
        }
        if (riskLevel.contains("MEDIUM")) {
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
