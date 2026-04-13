package com.jojo.prompt.common.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.entity.Prompt;
import com.jojo.prompt.mapper.PromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
//原创性检测
public class OriginalityReviewHandler extends AbstractReviewHandler {

    private final PromptMapper promptMapper;

    //相似度阈值，实际项目可以调整
    private static final double SIMILARITY_THRESHOLD = 0.8;

    @Override
    protected void doReview(Prompt prompt) {
        log.info("[{}] start checking: promptId={}", getHandlerName(), prompt.getId());

        //查询已存在的提示词
        List<Prompt> existingPrompts = promptMapper.selectList(
                new LambdaQueryWrapper<Prompt>()
                        .eq(Prompt::getCategoryId, prompt.getCategoryId())
                        .ne(prompt.getId() != null, Prompt::getId, prompt.getId())
        );
        //计算相似度
        for(Prompt existing : existingPrompts) {
            double similarity = calculateSimilarity(prompt.getContent(), existing.getContent());

            if(similarity > SIMILARITY_THRESHOLD) {
                log.info("[{}] check fail: promptId={}", getHandlerName(), prompt.getId());
                throw new BusinessException("prompt is too similar to existing prompt, similarity=(%.2f%%)" + similarity * 100);
            }
        }
        log.info("[{}] check success: promptId={}", getHandlerName(), prompt.getId());
    }

    private double calculateSimilarity(String text1, String text2) {
        //可调用AI API进行语义相似度检查
        //目前先用简单字符串重复率
        if(text1 == null || text2 == null) {
            return 0.0;
        }
        int lcs = longestCommonSubsequence(text1, text2);
        int maxLen = Math.max(text1.length(), text2.length());

        return (double) lcs / maxLen;
    }

    private int longestCommonSubsequence(String text1, String text2) {
        int m = text1.length();
        int n = text2.length();

        int[][] dp = new int[m + 1][n + 1];

        for(int i = 1; i <= m; i++) {
            for(int j = 1; j <= n; j++) {
                if(text1.charAt(i - 1) ==  text2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                }else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return  dp[m][n];
    }

    @Override
    protected String getHandlerName() {
        return "originalityReviewHandler";
    }
}
