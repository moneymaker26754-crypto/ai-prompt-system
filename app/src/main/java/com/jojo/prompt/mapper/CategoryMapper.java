package com.jojo.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jojo.prompt.dto.response.CategoryVO;
import com.jojo.prompt.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    //统计该分类下提示词的数量
    @Select("select count(*) from prompt where category_id = #{categoryId} and deleted = 0")
    Long countPromptByCategoryId(@Param("categoryId") Long categoryId);

    List<CategoryVO> selectAllCategoriesWithCount();
}
