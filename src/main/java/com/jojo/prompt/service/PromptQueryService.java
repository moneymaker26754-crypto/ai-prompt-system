package com.jojo.prompt.service;

import com.jojo.prompt.common.result.PageResult;
import com.jojo.prompt.dto.request.PromptQueryDTO;
import com.jojo.prompt.dto.response.PromptVO;

import java.util.List;

public interface PromptQueryService {
    PromptVO queryPromptById(Long id);

    PageResult<PromptVO> queryPage(PromptQueryDTO query, Integer pageNo, Integer pageSize);

    PageResult<PromptVO> queryMyPage(PromptQueryDTO query, Integer pageNo, Integer pageSize);
    //热门提示词排行
    List<PromptVO> getHotList(String type, int limit);

}
