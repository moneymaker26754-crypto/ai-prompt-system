package com.jojo.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jojo.prompt.entity.SearchHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {
    void incrementSearchCount(@Param("id") Long id);
}
