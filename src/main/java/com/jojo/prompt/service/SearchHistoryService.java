package com.jojo.prompt.service;

import java.util.List;

public interface SearchHistoryService {
    //记录搜索历史
    void recordHistory(String keyword);
    //获取我的搜索历史
    List<String> queryMyHistory();
    //清空搜索历史
    void clearHistory();

    List<String> getHotSearchKeywords();
}
