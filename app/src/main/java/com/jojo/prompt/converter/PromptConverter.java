package com.jojo.prompt.converter;

import cn.hutool.core.bean.BeanUtil;
import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Category;
import com.jojo.prompt.entity.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptConverter {
    //entity转vo(不含数据类的)
    public PromptVO toVO(Prompt prompt) {
        if(prompt == null) {
            return null;
        }
        PromptVO vo = BeanUtil.copyProperties(prompt, PromptVO.class);

        if(StringUtils.hasText(prompt.getTags())) {
            vo.setTagList(Arrays.asList(prompt.getTags().split(",")));
        }else {
            vo.setTagList(Collections.emptyList());
        }
        return vo;
    }
    //entity转vo(包含分类名)
    public PromptVO toVO(Prompt prompt, Category category) {
        PromptVO vo = toVO(prompt);
        if(prompt != null && category != null) {
            vo.setCategoryName(category.getName());
        }
        return vo;
    }
    //entity list转vo list
    public List<PromptVO> toVOList(List<Prompt> prompts) {
        if(prompts == null || prompts.isEmpty()) {
            return Collections.emptyList();
        }
        return prompts.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }
}
