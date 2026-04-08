package com.jojo.prompt.controller;

import com.jojo.prompt.common.result.PageResult;
import com.jojo.prompt.common.result.Result;
import com.jojo.prompt.dto.response.PromptFavoriteVO;
import com.jojo.prompt.service.PromptFavoriteService;
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
@Tag(name = "提示词收藏",description = "收藏，取消收藏，我的收藏")
public class PromptFavoriteController {

    private final PromptFavoriteService promptFavoriteService;

    @Operation(summary = "收藏提示词")
    @PostMapping("/{id}/favorite")
    public Result<Void> favoritePrompt(@Parameter(description = "提示词ID") @PathVariable Long id) {
        promptFavoriteService.favoritePrompt(id);
        return Result.success("favorite success", null);
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavoritePrompt(@Parameter(description = "提示词ID") @PathVariable Long id) {
        promptFavoriteService.unfavoritePrompt(id);
        return Result.success("success", null);
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping("/myFavorites")
    public Result<PageResult<PromptFavoriteVO>> getMyFavorites(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNo,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize
    ) {
        PageResult<PromptFavoriteVO> pageResult = promptFavoriteService.queryMyFavoritePrompt(pageNo, pageSize);
        return Result.success(pageResult);
    }
}
