package com.jojo.prompt.service;

import com.jojo.prompt.dto.request.PromptOptimizeConfirmDTO;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.PromptOptimizeVO;

public interface PromptOptimizationService {

    PromptOptimizeVO optimize(PromptOptimizeRequestDTO dto);

    PromptOptimizeVO getById(Long id);

    //保存正式Prompt的接口
    Long confirmAsPrompt(PromptOptimizeConfirmDTO dto);
}
