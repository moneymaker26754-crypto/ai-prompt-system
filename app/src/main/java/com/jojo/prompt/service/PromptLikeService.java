package com.jojo.prompt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jojo.prompt.dto.response.PromptFavoriteVO;

public interface PromptLikeService {
    //点赞的提示词
    void likePrompt(Long id);
    //取消点赞
    void unLikePrompt(Long id);
    //检查是否已点赞
    boolean isLiked(Long id, Long userId);

}
