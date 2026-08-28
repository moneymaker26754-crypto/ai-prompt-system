package com.jojo.prompt.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.handler.optimization.PromptOptimizeReviewContext;
import com.jojo.prompt.common.handler.optimization.PromptOptimizeReviewHandler;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.PromptOptimizeReviewResult;
import com.jojo.prompt.dto.response.PromptOptimizeStreamEvent;
import com.jojo.prompt.dto.response.PromptOptimizeVO;
import com.jojo.prompt.entity.PromptOptimizationRecord;
import com.jojo.prompt.entity.PromptTemplate;
import com.jojo.prompt.integration.ai.*;
import com.jojo.prompt.mapper.PromptOptimizationRecordMapper;
import com.jojo.prompt.mapper.PromptTemplateMapper;
import com.jojo.prompt.service.PromptOptimizationStreamService;
import com.jojo.prompt.service.agent.PromptAnalyzeAgent;
import com.jojo.prompt.service.agent.PromptOptimizeStreamAgent;
import com.jojo.prompt.service.agent.PromptReviewAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptOptimizationStreamServiceImpl implements PromptOptimizationStreamService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PromptOptimizationRecordMapper recordMapper;
    private final PromptPermissionService promptPermissionService;
    private final PromptOptimizeReviewHandler promptOptimizeReviewChain;
    //    private final PromptAnalyzeAgent analyzeAgent;
//    private final PromptOptimizeStreamAgent optimizeStreamAgent;
//    private final PromptReviewAgent reviewAgent;
    // 对于 AI 的调用都去用 Python 服务去实现
    private final PromptAiGateway promptAiGateway;
    private final PromptAiStreamGateway promptAiStreamGateway;
    private final ObjectMapper objectMapper;


    @Override
    public Flux<ServerSentEvent<PromptOptimizeStreamEvent>> optimizeStream(PromptOptimizeRequestDTO dto) {
        return Flux.defer(() -> {
            Long userId = promptPermissionService.requireCurrentUserId();

            PromptTemplate template = promptTemplateMapper.selectById(dto.getTemplateId());
            if (template == null) {
                throw new BusinessException("template not exist");
            }

            //创建待审核的提示词文本
            PromptOptimizeReviewContext context = new PromptOptimizeReviewContext();
            context.setUserId(userId);
            context.setTemplate(template);
            context.setRequest(dto);

            //执行审核链
            promptOptimizeReviewChain.review(context);

            //调用 Python 服务的 analyze 开始分析
            PromptAnalyzeResult analyzeResult = promptAiGateway.analyze(dto.getOriginalPrompt(), template);
            String analysis = analyzeResult.analysis();
            StringBuilder optimizedBuilder = new StringBuilder();

            Flux<ServerSentEvent<PromptOptimizeStreamEvent>> startEvent = Flux.just(
                    event("REVIEW", "责任链审核通过"),
                    event("ANALYSIS", analysis),
                    event("OPTIMIZING", "")
            );

            //调用optimizeStreamAgent边返回token，边拼接完整结果
//            Flux<ServerSentEvent<PromptOptimizeStreamEvent>> optimizeEvent =
//                    optimizeStreamAgent.optimizeStream(dto, template, analysis)
//                            .doOnNext(optimizedBuilder::append)
//                            .map(token -> event("TOKEN", token));

            // 利用 Python 服务的 optimizeStream 开始优化
            Flux<ServerSentEvent<PromptOptimizeStreamEvent>> optimizeEvent =
                    promptAiStreamGateway
                            .optimizeStream(dto, template, analysis)
                            .handle((chunk, sink) -> {

                                if ("error".equals(chunk.type())) {
                                    sink.error(new BusinessException(
                                            "AI stream failed ["
                                                    + chunk.code()
                                                    + "]: "
                                                    + chunk.content()
                                    ));
                                    return;
                                }

                                if ("token".equals(chunk.type())) {

                                    optimizedBuilder.append(chunk.content());

                                    sink.next(event("TOKEN", chunk.content()));
                                }
                            });

            //流式生成结束后，拿到完整优化结果，调用 PromptReviewAgent做JSON审核，保存优化记录，返回DONE事件。
            Mono<ServerSentEvent<PromptOptimizeStreamEvent>> doneEvent =
                    Mono.fromCallable(() -> {
                        String optimizedPrompt = optimizedBuilder.toString();
                        PromptReviewResult reviewResult =
                                promptAiGateway.review(dto.getOriginalPrompt(), optimizedPrompt);
                        //兜底再落库，避免出现空异常
//                        Integer score = extractScore(reviewResult);
//                        String riskLevel = extractRiskLevel(reviewResult);

                        PromptOptimizationRecord record = new PromptOptimizationRecord();
                        record.setUserId(userId);
                        record.setTemplateId(template.getId());
                        record.setOriginalPrompt(dto.getOriginalPrompt());
                        record.setAnalysisResult(analysis);
                        record.setOptimizedPrompt(optimizedPrompt);
                        record.setReviewResult(reviewResult == null ? null : reviewResult.reviewComment());
                        record.setScore(reviewResult.score());
                        record.setRiskLevel(reviewResult.riskLevel());
                        record.setReviewReport(objectMapper.writeValueAsString(context.getSteps()));
                        record.setModelName("qwen3.5:9b");
                        record.setStatus("SUCCESS");
                        recordMapper.insert(record);

                        String doneJson = objectMapper.writeValueAsString(Map.of(
                                "recordId", record.getId(),
                                "score", reviewResult.score(),
                                "riskLevel", reviewResult.riskLevel()
                        ));

                        return event("DONE", doneJson);
                    });

            //concatWith保证顺序执行
            return startEvent.concatWith(optimizeEvent).concatWith(doneEvent);
        });
    }

    private ServerSentEvent<PromptOptimizeStreamEvent> event(String stage, String content) {
        return ServerSentEvent.builder(new PromptOptimizeStreamEvent(stage, content))
                .event(stage)
                .build();
    }

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
}
