package com.jojo.prompt.controller;

import com.jojo.prompt.common.result.Result;
import com.jojo.prompt.dto.request.PromptOptimizeConfirmDTO;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.PromptOptimizeStreamEvent;
import com.jojo.prompt.dto.response.PromptOptimizeVO;
import com.jojo.prompt.service.PromptOptimizationService;
import com.jojo.prompt.service.PromptOptimizationStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/api/prompt-optimizations")
@RequiredArgsConstructor
@Tag(name = "提示词优化", description = "优化提示词")
public class PromptOptimizationController {

    private final PromptOptimizationService promptOptimizationService;
    private final PromptOptimizationStreamService promptOptimizationStreamService;

    @Operation(summary = "优化提示词")
    @PostMapping
    public Result<PromptOptimizeVO> optimize(@RequestBody @Valid PromptOptimizeRequestDTO dto) {
        return Result.success(promptOptimizationService.optimize(dto));
    }

    //流式输出接口
    @Operation(summary = "流式优化提示词")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<PromptOptimizeStreamEvent>> optimizeStream(
            @RequestBody @Valid PromptOptimizeRequestDTO dto) {
        return promptOptimizationStreamService.optimizeStream(dto);
    }

    @Operation(summary = "根据ID查询优化的提示词")
    @GetMapping("/{id}")
    public Result<PromptOptimizeVO> getById(@Parameter(description = "优化记录ID")
                                            @PathVariable Long id) {
        return Result.success(promptOptimizationService.getById(id));
    }

    //保存优化好的提示词
    @Operation(summary = "保存优化的提示词")
    @PostMapping("/confirm")
    public Result<Long> confirmAsPrompt(@RequestBody @Valid PromptOptimizeConfirmDTO dto) {
        Long promptId = promptOptimizationService.confirmAsPrompt(dto);
        return Result.success("confirm success", promptId);
    }
}
