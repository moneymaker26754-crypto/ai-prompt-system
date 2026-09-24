package com.jojo.prompt.service;

import com.jojo.prompt.dto.request.CategoryCreateDTO;
import com.jojo.prompt.dto.request.CategoryUpdateDTO;
import com.jojo.prompt.dto.response.CategoryVO;
import java.util.List;

public interface CategoryService {
    //创建分类
    Long createCategory(CategoryCreateDTO dto);
    //更新分类
    void updateCategory(CategoryUpdateDTO dto);
    //删除分类
    void deleteCategory(Long id);
    //根据id查询分类
    CategoryVO getCategoryById(Long id);
    //查询所有分类(按照排序字段升序)
    List<CategoryVO> getAllCategories();
    //查询所有分类(包含提示词数量统计)
    List<CategoryVO> getAllCategoriesWithCount();
}
