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
import com.jojo.prompt.integration.ai.PromptAiGateway;
import com.jojo.prompt.integration.ai.PromptAnalyzeResult;
import com.jojo.prompt.integration.ai.PromptOptimizeResult;
import com.jojo.prompt.integration.ai.PromptReviewResult;
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
    //private final PromptAnalyzeAgent promptAnalyzeAgent;
    //更换依赖
    private final PromptAiGateway promptAiGateway;
    //private final PromptOptimizeAgent promptOptimizeAgent;
    //private final PromptReviewAgent promptReviewAgent;
    private final PromptOptimizationConverter promptOptimizationConverter;
    private final ObjectMapper objectMapper;
    //用于创建优化好后的Prompt
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

        try {
            //agent优化工作流
            log.info("prompt optimize start, templateId={}, userId={}", dto.getTemplateId(), userId);

            log.info("prompt analyze start");
            //用 PromptAiGateway 去获取 Python 调用的 Ollama 或 Spring AI 的分析结果
            PromptAnalyzeResult analyzeResult = promptAiGateway.analyze(dto.getOriginalPrompt(), template);
            String analysisResult = analyzeResult.analysis();
            log.info("prompt analyze done");

            log.info("prompt optimize start");
            // 用 PromptAiGateway 去获取 Python 调用的 Ollama 或 Spring AI 的优化结果,之后再获取优化后的 Prompt
            PromptOptimizeResult optimizeResult = promptAiGateway.optimize(dto, template, analysisResult);
            String optimizedPrompt = optimizeResult.optimizePrompt();
            log.info("prompt optimize done");

            // 用 PromptAiGateway 去获取 Python 调用的 Ollama 或 Spring AI 的审核结果
            log.info("prompt review start");
            PromptReviewResult reviewResult = promptAiGateway.review(dto.getOriginalPrompt(), optimizedPrompt);
            log.info("prompt review done");

            record.setModelName(analyzeResult.model());
            record.setAnalysisResult(analysisResult);
            record.setOptimizedPrompt(optimizedPrompt);
            record.setReviewResult(reviewResult.reviewComment());
            record.setScore(reviewResult.score());
            record.setRiskLevel(reviewResult.riskLevel());
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

    //交给 Python 服务去判断，这些方法不再需要
//    private Integer extractScore(PromptOptimizeReviewResult reviewResult) {
//        if (reviewResult == null || reviewResult.getScore() == null) {
//            return 0;
//        }
//        return Math.max(0, Math.min(100, reviewResult.getScore()));
//    }
//
//    private String extractRiskLevel(PromptOptimizeReviewResult reviewResult) {
//        if (reviewResult == null || !StringUtils.hasText(reviewResult.getRiskLevel())) {
//            return "LOW";
//        }
//
//        String riskLevel = reviewResult.getRiskLevel().toUpperCase();
//        if (riskLevel.contains("HIGH")) {
//            return "HIGH";
//        }
//        if (riskLevel.contains("MEDIUM")) {
//            return "MEDIUM";
//        }
//        return "LOW";
//    }

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
