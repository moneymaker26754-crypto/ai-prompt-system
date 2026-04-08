package com.jojo.prompt.service;

import com.jojo.prompt.common.result.PageResult;
import com.jojo.prompt.dto.response.PromptFavoriteVO;

public interface PromptFavoriteService {
    //收藏提示词
    void favoritePrompt(Long id);
    //取消收藏
    void unfavoritePrompt(Long id);
    //检查是否已收藏
    boolean isFavoritePrompt(Long id, Long userId);
    //检查我的收藏列表
    PageResult<PromptFavoriteVO> queryMyFavoritePrompt(int pageNo, int pageSize);

}
