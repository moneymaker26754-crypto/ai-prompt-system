package com.jojo.prompt.converter;

import cn.hutool.core.bean.BeanUtil;
import com.jojo.prompt.dto.response.CategoryVO;
import com.jojo.prompt.entity.Category;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CategoryConverter {
    //entity转vo
    public CategoryVO toVO(Category category) {
        if(category == null) {
            return null;
        }
        CategoryVO vo = BeanUtil.copyProperties(category, CategoryVO.class);
        return vo;
    }

    //包含提示词数量
    public CategoryVO toVO(Category category, Long promptCount) {
        CategoryVO vo = toVO(category);
        if(vo != null) {
            vo.setPromptCount(promptCount);
        }
        return vo;
    }

    //entity list转vo list
    public List<CategoryVO> toVOList(List<Category> categories) {
        if(categories == null ||  categories.isEmpty()) {
            return Collections.emptyList();
        }
        return categories.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }
}
