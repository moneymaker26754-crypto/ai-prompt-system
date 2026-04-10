package com.jojo.prompt.service;

import com.jojo.prompt.dto.request.PromptCreateDTO;
import com.jojo.prompt.dto.request.PromptUpdateDTO;

public interface PromptCommandService {

    Long createPrompt(PromptCreateDTO dto);

    void updatePrompt(PromptUpdateDTO dto);

    void deletePrompt(Long id);

    //复制提示词
    String copyPrompt(Long id);
}
