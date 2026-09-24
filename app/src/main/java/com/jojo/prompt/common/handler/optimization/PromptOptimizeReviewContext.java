package com.jojo.prompt.common.handler.optimization;

import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import com.jojo.prompt.dto.response.ReviewStepVO;
import com.jojo.prompt.entity.PromptTemplate;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PromptOptimizeReviewContext {

    private Long userId;

    private PromptTemplate template;

    private PromptOptimizeRequestDTO request;

    private List<ReviewStepVO> steps = new ArrayList<>();
}
