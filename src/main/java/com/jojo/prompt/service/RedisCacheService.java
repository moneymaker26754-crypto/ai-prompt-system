package com.jojo.prompt.service;

import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Category;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RedisCacheService {

    //提示词缓存
    void cachePromptDetail(Long promptId, PromptVO promptVO);
    PromptVO getPromptDetailCache(Long promptId);
    void deletePromptCache(Long promptId);
    void cachePromptNull(Long promptId);
    boolean isPromptNullCache(Long promptId);

    //计数器操作
    Long incrementViewCount(Long promptId);
    Long incrementLikeCount(Long promptId);
    Long incrementFavoriteCount(Long promptId);
    Long incrementCopyCount(Long promptId);
    Long decrementLikeCount(Long promptId);
    Long decrementFavoriteCount(Long promptId);

    Map<String, Long> getPromptCounts(Long promptId);
    boolean syncCountToDb(Long promptId);
    //应对计数脏读问题
    void addDirtyPromptId(Long promptId);
    Set<Long> getDirtyPromptId();
    void removeDirtyPromptId(Set<Long> promptIds);

    //排行榜操作
    void updateHotRanking(Long promptId, String rankingType, double scoreDelta);
    List<Long> getHotRanking(String rankingType, int topN);

    //搜索热门提示词
    void incrementSearchKeyWord(String keyword);
    List<String> getHotSearchKeyWords(int topN);

    //用户行为缓存
    void addUserLike(Long userId, Long promptId);
    void removeUserLike(Long userId, Long promptId);
    boolean isUserLiked(Long userId, Long promptId);

    void addUserFavorite(Long userId, Long promptId);
    void removeUserFavorite(Long userId, Long promptId);
    boolean isUserFavorite(Long userId, Long promptId);

    //分类缓存
    void cacheCategoryList(List<Category> categories);
    List<Category> getCategoryListCache();
    void deleteCategoryCache();

    //分布式锁，用lua脚本实现锁释放
    Boolean tryLock(String lockKey, String requestId, long expireTime);

    //限流相关
    boolean trySearchAllowed(String identifier, long limit, long windowSeconds);

    boolean tryRecordCopyCount(String identifier, Long promptId, long windowSeconds);

    boolean tryLoginAllowed(String ipIdentifier, long limit, long windowSeconds);
    boolean isLoginBlocked(String failIdentifier);
    long recordLoginFailure(String failIdentifier, long windowSeconds, long threshold, long blockSeconds);
    void clearLoginFailure(String failIdentifier);
}
