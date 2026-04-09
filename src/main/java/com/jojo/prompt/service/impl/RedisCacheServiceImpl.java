package com.jojo.prompt.service.impl;

import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Category;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.jojo.prompt.common.constant.RedisKeyConstant.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final PromptMapper promptMapper;

    @Override
    public void cachePromptDetail(Long promptId, PromptVO promptVO) {
        String key = PROMPT_DEFAULT + promptId;
        redisTemplate.opsForValue().set(key, promptVO, CACHE_EXPIRE_30MIN, TimeUnit.SECONDS);
    }

    @Override
    public PromptVO getPromptDetailCache(Long promptId) {
        String key = PROMPT_DEFAULT + promptId;
        return (PromptVO) redisTemplate.opsForValue().get(key);
    }

    @Override
    public void deletePromptCache(Long promptId) {
        String key = PROMPT_DEFAULT + promptId;
        redisTemplate.delete(key);
    }

    @Override
    public void cachePromptNull(Long promptId) {
        String key = PROMPT_NULL + promptId;
        stringRedisTemplate.opsForValue().set(key, "1", 5, TimeUnit.MINUTES);
    }

    @Override
    public boolean isPromptNullCache(Long promptId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(PROMPT_NULL + promptId));
    }

    @Override
    public Long incrementViewCount(Long promptId) {
        String key = PROMPT_VIEW_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, CACHE_EXPIRE_1DAY, TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "view", getDeltaOrZero(key));
        return count;
    }

    @Override
    public Long incrementLikeCount(Long promptId) {
        String key = PROMPT_LIKE_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, CACHE_EXPIRE_1DAY, TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "like", getDeltaOrZero(key));
        return count;
    }

    @Override
    public Long incrementFavoriteCount(Long promptId) {
        String key = PROMPT_FAVORITE_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, CACHE_EXPIRE_1DAY, TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "favorite", getDeltaOrZero(key));
        return count;
    }

    @Override
    public Long incrementCopyCount(Long promptId) {
        String key = PROMPT_COPY_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, CACHE_EXPIRE_1DAY, TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "copy", getDeltaOrZero(key));
        return count;
    }

    @Override
    public Long decrementLikeCount(Long promptId) {
        String key = PROMPT_LIKE_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key, -1);
        stringRedisTemplate.expire(key, CACHE_EXPIRE_1DAY, TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "like", getDeltaOrZero(key));
        return count;
    }

    @Override
    public Long decrementFavoriteCount(Long promptId) {
        String key = PROMPT_FAVORITE_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key, -1);
        stringRedisTemplate.expire(key, CACHE_EXPIRE_1DAY, TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "favorite", getDeltaOrZero(key));
        return count;
    }
    private double getDeltaOrZero(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if(value == null) {
            return 0D;
        }
        return Double.parseDouble(value);
    }
    @Override
    public Map<String, Long> getPromptCounts(Long promptId) {
        Map<String, Long> counts = new HashMap<>();
        String viewCount = stringRedisTemplate.opsForValue().get(PROMPT_VIEW_COUNT + promptId);
        String likeCount = stringRedisTemplate.opsForValue().get(PROMPT_LIKE_COUNT + promptId);
        String favoriteCount = stringRedisTemplate.opsForValue().get(PROMPT_FAVORITE_COUNT + promptId);
        String copyCount = stringRedisTemplate.opsForValue().get(PROMPT_COPY_COUNT + promptId);

        counts.put("viewCount", viewCount != null ? Long.parseLong(viewCount) : 0L);
        counts.put("likeCount", likeCount != null ? Long.parseLong(likeCount) : 0L);
        counts.put("favoriteCount", favoriteCount != null ? Long.parseLong(favoriteCount) : 0L);
        counts.put("copyCount", copyCount != null ? Long.parseLong(copyCount) : 0L);

        return counts;
    }

    @Override
    public void syncCountToDb(Long promptId) {
        Map<String, Long> counts = getPromptCounts(promptId);
        if(counts.values().stream().allMatch(count -> count == 0L)) {
            return;
        }
        promptMapper.incrementCounts(
                promptId,
                counts.get("viewCount").intValue(),
                counts.get("likeCount").intValue(),
                counts.get("favoriteCount").intValue(),
                counts.get("copyCount").intValue()
        );
        stringRedisTemplate.delete(List.of(
                PROMPT_VIEW_COUNT + promptId,
                PROMPT_LIKE_COUNT + promptId,
                PROMPT_FAVORITE_COUNT + promptId,
                PROMPT_COPY_COUNT + promptId
        ));
    }

    @Override
    public void addDirtyPromptId(Long promptId) {
        stringRedisTemplate.opsForSet().add(PROMPT_COUNT_DIRTY_SET, promptId.toString());
        stringRedisTemplate.expire(PROMPT_COUNT_DIRTY_SET, CACHE_EXPIRE_1DAY, TimeUnit.SECONDS);
    }

    @Override
    public Set<Long> getDirtyPromptId() {
        Set<String> members = stringRedisTemplate.opsForSet().members(PROMPT_COUNT_DIRTY_SET);
        if(members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    @Override
    public void removeDirtyPromptId(Set<Long> promptIds) {
        if(promptIds == null || promptIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForSet().remove(
                PROMPT_COUNT_DIRTY_SET,
                promptIds.stream()
                .map(String::valueOf)
                .toArray(String[]::new));

    }

    @Override
    public void updateHotRanking(Long promptId, String rankingType, double score) {
        String key = switch(rankingType) {
            case "like" -> PROMPT_HOT_LIKE;
            case "view" -> PROMPT_HOT_VIEW;
            case "favorite" -> PROMPT_HOT_FAVORITE;
            case "copy" -> PROMPT_HOT_COPY;
            default -> null;
        };
        if(key != null) {
            stringRedisTemplate.opsForZSet().add(key, promptId.toString(), score);
            stringRedisTemplate.expire(key, CACHE_EXPIRE_1DAY, TimeUnit.SECONDS);
        }
    }

    @Override
    public List<Long> getHotRanking(String rankingType, int topN) {
        String key = switch (rankingType) {
            case "like" -> PROMPT_HOT_LIKE;
            case "view" -> PROMPT_HOT_VIEW;
            case "favorite" -> PROMPT_HOT_FAVORITE;
            case "copy" -> PROMPT_HOT_COPY;
            default -> null;
        };

        if (key == null) {
            return Collections.emptyList();
        }

        Set<String> result = stringRedisTemplate.opsForZSet().reverseRange(key, 0, topN - 1);
        return result != null ? result.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList()) : Collections.emptyList();
    }

    @Override
    public void incrementSearchKeyWord(String keyword) {
        stringRedisTemplate.opsForZSet().incrementScore(SEARCH_HOT_KEYWORDS, keyword, 1.0);
        stringRedisTemplate.expire(SEARCH_HOT_KEYWORDS, CACHE_EXPIRE_7DAY, TimeUnit.SECONDS);
    }

    @Override
    public List<String> getHotSearchKeyWords(int topN) {
        Set<String> result = stringRedisTemplate.opsForZSet().reverseRange(SEARCH_HOT_KEYWORDS, 0, topN - 1);
        return result != null ? new ArrayList<>(result) : Collections.emptyList();
    }

    @Override
    public void addUserLike(Long userId, Long promptId) {
        String key = USER_LIKE_SET + userId;
        stringRedisTemplate.opsForSet().add(key, promptId.toString());
        stringRedisTemplate.expire(key, CACHE_EXPIRE_7DAY, TimeUnit.SECONDS);
    }

    @Override
    public void removeUserLike(Long userId, Long promptId) {
        String key = USER_LIKE_SET + userId;
        stringRedisTemplate.opsForSet().remove(key, promptId.toString());
    }

    @Override
    public boolean isUserLiked(Long userId, Long promptId) {
        String key = USER_LIKE_SET + userId;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, promptId.toString()));
    }

    @Override
    public void addUserFavorite(Long userId, Long promptId) {
        String key  = USER_FAVORITE_SET + userId;
        stringRedisTemplate.opsForSet().add(key, promptId.toString());
        stringRedisTemplate.expire(key, CACHE_EXPIRE_7DAY, TimeUnit.SECONDS);
    }

    @Override
    public void removeUserFavorite(Long userId, Long promptId) {
        String key = USER_FAVORITE_SET + userId;
        stringRedisTemplate.opsForSet().remove(key, promptId.toString());
    }

    @Override
    public boolean isUserFavorite(Long userId, Long promptId) {
        String key = USER_FAVORITE_SET + userId;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, promptId.toString()));
    }

    @Override
    public void cacheCategoryList(List<Category> categories) {
        redisTemplate.opsForValue().set(CATEGORY_LIST, categories, CACHE_EXPIRE_1HOUR, TimeUnit.SECONDS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Category> getCategoryListCache() {
        return (List<Category>) redisTemplate.opsForValue().get(CATEGORY_LIST);
    }

    @Override
    public void deleteCategoryCache() {
        redisTemplate.delete(CATEGORY_LIST);
    }
}
