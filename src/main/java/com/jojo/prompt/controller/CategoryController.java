package com.jojo.prompt.controller;

import com.jojo.prompt.common.result.Result;
import com.jojo.prompt.dto.request.CategoryCreateDTO;
import com.jojo.prompt.dto.request.CategoryUpdateDTO;
import com.jojo.prompt.dto.response.CategoryVO;
import com.jojo.prompt.service.CategoryService;
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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "分类管理", description = "提示词分类管理接口")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "创建分类")
    @PostMapping
    public Result<Long> createCategory(@RequestBody @Valid CategoryCreateDTO dto) {
        Long id = categoryService.createCategory(dto);
        return Result.success("create success", id);
    }
    @Operation(summary = "更新分类")
    @PutMapping
    public Result<Void> updateCategory(@RequestBody @Valid CategoryUpdateDTO dto) {
        categoryService.updateCategory(dto);
        return Result.success("update success", null);
    }
    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@Parameter(description = "分类ID") @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success("delete success", null);
    }
    @Operation(summary = "查询分类详细")
    @GetMapping("/{id}")
    public Result<CategoryVO> getCategoryById(@Parameter(description = "分类ID")  @PathVariable Long id) {
        CategoryVO category = categoryService.getCategoryById(id);
        return Result.success(category);
    }
    @Operation(summary = "查询所有分类")
    @GetMapping("/list")
    public Result<List<CategoryVO>> getAllCategories() {
        List<CategoryVO> categories = categoryService.getAllCategories();
        return Result.success(categories);
    }
    @Operation(summary = "查询所有分类及提示词数量")
    @GetMapping("/list-with-count")
    public Result<List<CategoryVO>> getAllCategoriesWithCount() {
        List<CategoryVO> categories = categoryService.getAllCategoriesWithCount();
        return Result.success(categories);
    }
}
