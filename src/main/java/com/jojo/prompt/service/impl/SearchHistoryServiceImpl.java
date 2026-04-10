package com.jojo.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jojo.prompt.entity.SearchHistory;
import com.jojo.prompt.mapper.SearchHistoryMapper;
import com.jojo.prompt.service.RedisCacheService;
import com.jojo.prompt.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {

    private final SearchHistoryMapper searchHistoryMapper;
    private final RedisCacheService redisCacheService;
    private PromptPermissionService promptPermissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordHistory(String keyword) {
        //不为空
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        //记录热门搜索词到redis
        redisCacheService.incrementSearchKeyWord(keyword);
        //登录后可查看
        Long userId = promptPermissionService.requireCurrentUserId();
        if (userId == null) {
            return;
        }
        SearchHistory history = searchHistoryMapper.selectOne(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .eq(SearchHistory::getKeyword, keyword)
        );
        if (history != null) {
            searchHistoryMapper.incrementSearchCount(history.getId());
        } else {
            history = new SearchHistory();
            history.setUserId(userId);
            history.setKeyword(keyword);
            history.setSearchCount(1);
            searchHistoryMapper.insert(history);
        }
        log.debug("recordHistory: userId={}, keyword={}", userId, keyword);
    }

    @Override
    public List<String> queryMyHistory() {
        Long userId = promptPermissionService.requireCurrentUserId();
        List<SearchHistory> histories = searchHistoryMapper.selectList(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .orderByDesc(SearchHistory::getUpdateTime)
                        .last("LIMIT 10")
        );
        return histories.stream()
                .map(SearchHistory::getKeyword)
                .collect(Collectors.toList());
    }

    @Override
    public void clearHistory() {
        Long userId = promptPermissionService.requireCurrentUserId();
        searchHistoryMapper.delete(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
        );
        log.info("clearHistory: userId={}", userId);
    }

    @Override
    public List<String> getHotSearchKeywords() {
        return redisCacheService.getHotSearchKeyWords(10);
    }

}
