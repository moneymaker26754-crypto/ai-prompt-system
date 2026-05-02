package com.jojo.prompt.controller;

import com.jojo.prompt.common.result.Result;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.PromptOptimizeVO;
import com.jojo.prompt.service.PromptOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/prompt-optimizations")
@RequiredArgsConstructor
@Tag(name = "提示词优化", description = "优化提示词")
public class PromptOptimizationController {

    private final PromptOptimizationService promptOptimizationService;

    @Operation(summary = "优化提示词")
    @PostMapping
    public Result<PromptOptimizeVO> optimize(@RequestBody @Valid PromptOptimizeRequestDTO dto) {
        return Result.success(promptOptimizationService.optimize(dto));
    }

    @Operation(summary = "根据ID查询优化的提示词")
    @GetMapping("/{id}")
    public Result<PromptOptimizeVO> getById(@Parameter(description = "优化记录ID")
                                            @PathVariable Long id) {
        return Result.success(promptOptimizationService.getById(id));
    }
}
