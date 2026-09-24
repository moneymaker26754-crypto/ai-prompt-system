package com.jojo.prompt.controller;

import com.jojo.prompt.common.result.Result;
import com.jojo.prompt.service.PromptLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
@Tag(name = "提示词点赞", description = "点赞,取消点赞")
public class PromptLikeController {

    private final PromptLikeService promptLikeService;

    @Operation(summary = "点赞提示词")
    @PostMapping("/{id}/like")
    public Result<Void> likePrompt(@Parameter(description =  "提示词ID") @PathVariable Long id) {
        promptLikeService.likePrompt(id);
        return Result.success("like success", null);
    }

    @Operation(summary = "取消点赞")
    @DeleteMapping("/{id}/like")
    public Result<Void> unlikePrompt(@Parameter(description =  "提示词ID") @PathVariable Long id) {
        promptLikeService.unLikePrompt(id);
        return Result.success("success", null);
    }
}
