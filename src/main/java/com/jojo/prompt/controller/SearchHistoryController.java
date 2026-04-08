package com.jojo.prompt.controller;

import com.jojo.prompt.common.result.Result;
import com.jojo.prompt.service.SearchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "搜索历史", description = "搜索历史记录管理")
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    @Operation(summary = "获取我的搜索历史")
    @GetMapping("/history")
    public Result<List<String>> getMyHistory() {
        List<String> history = searchHistoryService.queryMyHistory();
        return Result.success(history);
    }

    @Operation(summary = "清空搜索历史")
    @DeleteMapping("/history")
    public Result<Void> clearHistory() {
        searchHistoryService.clearHistory();
        return Result.success("clear", null);
    }

    @Operation(summary = "获取热门搜索词")
    @GetMapping("/hot")
    public Result<List<String>> getHotKeywords() {
        return Result.success((searchHistoryService.getHotSearchKeywords()));
    }

}