package com.jojo.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jojo.prompt.common.constant.PromptStatus;
import com.jojo.prompt.common.constant.PromptVisibility;
import com.jojo.prompt.entity.Prompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromptMapper extends BaseMapper<Prompt> {

    Page<Prompt> searchMyFullText(Page<Prompt> page, @Param("userId") Long userId, @Param("keyword") String keyword);

    Page<Prompt> searchPublicFullText(Page<Prompt> page, @Param("keyword") String keyword, @Param("visibility") PromptVisibility visibility, @Param("status") PromptStatus status);

    void incrementCounts(@Param("promptId") Long promptId, @Param("viewCount") Integer viewCount, @Param("likeCount") Integer likeCount, @Param("favoriteCount") Integer favoriteCount, @Param("copyCount") Integer copyCount);
}
