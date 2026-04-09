package com.jojo.prompt.controller;

import com.jojo.prompt.common.result.PageResult;
import com.jojo.prompt.common.result.Result;
import com.jojo.prompt.dto.request.PromptCreateDTO;
import com.jojo.prompt.dto.request.PromptQueryDTO;
import com.jojo.prompt.dto.request.PromptUpdateDTO;
import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.PromptService;
import com.jojo.prompt.service.RedisCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
@Tag(name = "提示词", description = "提示词管理接口")
public class PromptController {
    private final PromptService promptService;
    private final PromptMapper promptMapper;
    private final RedisCacheService redisCacheService;

    @Operation(summary = "创建提示词")
    @PostMapping
    public Result<Long> createPrompt(@RequestBody @Valid PromptCreateDTO dto) {
        Long id = promptService.createPrompt(dto);
        return Result.success("create success", id);
    }

    @Operation(summary = "更新提示词")
    @PutMapping
    public Result<Void> updatePrompt(@RequestBody @Valid PromptUpdateDTO dto) {
        promptService.updatePrompt(dto);
        return Result.success("update success", null);
    }

    @Operation(summary = "删除提示词")
    @DeleteMapping("/{id}")
    public Result<Void> deletePrompt(@Parameter(description = "prompt id") @PathVariable("id") Long id) {
        promptService.deletePrompt(id);
        return Result.success("delete success", null);
    }

    @Operation(summary = "获取提示词详细")
    @GetMapping("/{id}")
    public Result<PromptVO> queryPromptById(@Parameter(description = "prompt id") @PathVariable("id") Long id) {
        PromptVO promptVO = promptService.queryPromptById(id);
        return Result.success(promptVO);
    }

    @Operation(summary = "获取公共提示词页面")
    @GetMapping("/page")
    public Result<PageResult<PromptVO>> queryPage(@Valid PromptQueryDTO query,
                                                  @Parameter(description = "page no") @RequestParam(defaultValue = "1") int pageNo,
                                                  @Parameter(description = "page size") @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<PromptVO> page = promptService.queryPage(query, pageNo, pageSize);
        return Result.success(page);
    }

    @Operation(summary = "获取当前用户的提示词")
    @GetMapping("/mine/page")
    public Result<PageResult<PromptVO>> queryMyPage(@Valid PromptQueryDTO query,
                                                    @Parameter(description = "page no") @RequestParam(defaultValue = "1") int pageNo,
                                                    @Parameter(description = "page size") @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<PromptVO> page = promptService.queryMyPage(query, pageNo, pageSize);
        return Result.success(page);
    }

    //1.5新增2.0改良
    @Operation(summary = "热门提示词排行")
    @GetMapping("/hot")
    public Result<List<PromptVO>> queryHotPrompt(
            @Parameter(description = "排行类型：点赞，收藏，浏览量，复制次数")
            @RequestParam(defaultValue = "updateTime") String type,
            @Parameter(description = "数量")
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<PromptVO> hotList = promptService.getHotList(type, limit);
        //返回热门ids对应的提示词集合
        return Result.success(hotList);
    }
    //1.5新增
    @Operation(summary = "复制提示词", description = "记录复制的提示词并返回内容")
    @GetMapping("{id}/copy")
    public Result<String> copyPrompt(@Parameter(description = "提示词ID") @PathVariable Long id) {
        String context = promptService.copyPrompt(id);
        return Result.success("copy success", context);
    }
}
