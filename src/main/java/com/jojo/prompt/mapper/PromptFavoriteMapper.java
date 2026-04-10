package com.jojo.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jojo.prompt.dto.response.PromptFavoriteListItem;
import com.jojo.prompt.entity.PromptFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromptFavoriteMapper extends BaseMapper<PromptFavorite> {
    Page<PromptFavoriteListItem> selectMyFavoritePromptPage(
            @Param("page") Page<PromptFavoriteListItem> page,
            @Param("promptId") Long promptId
    );
}
