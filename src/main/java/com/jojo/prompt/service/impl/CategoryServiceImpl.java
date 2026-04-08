package com.jojo.prompt.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.converter.CategoryConverter;
import com.jojo.prompt.dto.request.CategoryCreateDTO;
import com.jojo.prompt.dto.request.CategoryUpdateDTO;
import com.jojo.prompt.dto.response.CategoryVO;
import com.jojo.prompt.entity.Category;
import com.jojo.prompt.mapper.CategoryMapper;
import com.jojo.prompt.service.CategoryService;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final CategoryConverter categoryConverter;
    private final RedisCacheService  redisCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(CategoryCreateDTO dto) {
        //检查分类名称是否存在
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, dto.getName())
        );
        if (count > 0) {
            throw new BusinessException("category name exist");
        }
        //创建分类
        Category category = BeanUtil.copyProperties(dto, Category.class);
        categoryMapper.insert(category);
        log.info("create category success: id={}", category.getId());
        //删除缓存
        redisCacheService.deleteCategoryCache();
        return category.getId();
    }

    @Override
    //用乐观锁
    public void updateCategory(CategoryUpdateDTO dto) {
        Category existing =  categoryMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("category not exist");
        }
        //检查是否与其他分类重复
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, dto.getName())
                        .ne(Category::getId, dto.getId())
        );
        if(count > 0) {
            throw new BusinessException("category name exist");
        }
        Category category = BeanUtil.copyProperties(dto, Category.class);
        int rows = categoryMapper.updateById(category);
        if(rows == 0) {
            throw new BusinessException("category has been modified by another user");
        }
        log.info("update category success: id={}, name={}", category.getId(),  category.getName());
        //删除缓存
        redisCacheService.deleteCategoryCache();
    }

    @Override
    public void deleteCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if(category == null) {
            throw new BusinessException("category not exist");
        }
        //检查该分类是否有以下提示词
        Long promptCount = categoryMapper.countPromptByCategoryId(id);
        if(promptCount > 0) {
            throw new BusinessException("this category still have " + promptCount + " prompts");
        }
        categoryMapper.deleteById(id);
        log.info("delete category success: id={}", id);
        //删除缓存
        redisCacheService.deleteCategoryCache();
    }

    @Override
    public CategoryVO getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if(category == null) {
            throw new BusinessException("category not exist");
        }
        Long promptCount = categoryMapper.countPromptByCategoryId(id);
        return categoryConverter.toVO(category, promptCount);
    }

    //缓存三问题
    @Override
    public List<CategoryVO> getAllCategories() {
        //先查缓存
        List<Category> cacheVO = redisCacheService.getCategoryListCache();
        if(cacheVO != null) {
            return categoryConverter.toVOList(cacheVO);
        }
        //查询数据库
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSortOrder)//按字段升序
                        .orderByAsc(Category::getCreateTime)//字段相同按时间升序
        );
        //写入缓存
        redisCacheService.cacheCategoryList(categories);

        return categoryConverter.toVOList(categories);
    }

    @Override
    public List<CategoryVO> getAllCategoriesWithCount() {
        //要调用N+1次sql，效率不佳
//        List<Category> categories = categoryMapper.selectList(
//                new LambdaQueryWrapper<Category>()
//                        .orderByAsc(Category::getSortOrder)//按字段升序
//                        .orderByAsc(Category::getCreateTime)//字段相同按时间升序
//        );
//        return categories.stream()
//                .map(category -> {
//                    Long promptCount = categoryMapper.countPromptByCategoryId(category.getId());
//                    return categoryConverter.toVO(category, promptCount);
//                })
//                .collect(Collectors.toList());
        //直接写入mapper层一次查询之后全部返回
        return categoryMapper.selectAllCategoriesWithCount();
    }
}
