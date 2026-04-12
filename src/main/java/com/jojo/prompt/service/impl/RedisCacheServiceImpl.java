package com.jojo.prompt.service.impl;

import com.jojo.prompt.dto.response.PromptVO;
import com.jojo.prompt.entity.Category;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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
    private final DefaultRedisScript<List> countSnapshotScript;
    private final DefaultRedisScript<List> countDeductScript;
    private final DefaultRedisScript<Long> unlockScript;

    @Override
    public void cachePromptDetail(Long promptId, PromptVO promptVO) {
        String key = PROMPT_DEFAULT + promptId;
        redisTemplate.opsForValue().set(key, promptVO, jitter(CACHE_EXPIRE_30MIN), TimeUnit.SECONDS);
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
        stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_1DAY), TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "view", 1.0);
        return count;
    }

    @Override
    public Long incrementLikeCount(Long promptId) {
        String key = PROMPT_LIKE_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_1DAY), TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "like", 1.0);
        return count;
    }

    @Override
    public Long incrementFavoriteCount(Long promptId) {
        String key = PROMPT_FAVORITE_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_1DAY), TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "favorite", 1.0);
        return count;
    }

    @Override
    public Long incrementCopyCount(Long promptId) {
        String key = PROMPT_COPY_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_1DAY), TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "copy", 1.0);
        return count;
    }

    @Override
    public Long decrementLikeCount(Long promptId) {
        String key = PROMPT_LIKE_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key, -1);
        stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_1DAY), TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "like", -1.0);
        return count;
    }

    @Override
    public Long decrementFavoriteCount(Long promptId) {
        String key = PROMPT_FAVORITE_COUNT + promptId;
        Long count = stringRedisTemplate.opsForValue().increment(key, -1);
        stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_1DAY), TimeUnit.SECONDS);
        addDirtyPromptId(promptId);
        updateHotRanking(promptId, "favorite", -1.0);
        return count;
    }
    //返回值为map主要是为了根据key的名称获取对应的值，直观
    @Override
    public Map<String, Long> getPromptCounts(Long promptId) {
        List<String> keys = List.of(
                PROMPT_VIEW_COUNT + promptId,
                PROMPT_LIKE_COUNT + promptId,
                PROMPT_FAVORITE_COUNT + promptId,
                PROMPT_COPY_COUNT + promptId
        );
        //从redis计数里获取对应keys的增量
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

        Map<String, Long> counts = new HashMap<>(4);
        counts.put("viewCount", parseOrZero(values, 0));
        counts.put("likeCount", parseOrZero(values, 1));
        counts.put("favoriteCount", parseOrZero(values, 2));
        counts.put("copyCount", parseOrZero(values, 3));
        return counts;
    }

    @Override
    public boolean syncCountToDb(Long promptId) {
        String lockKey = PROMPT_COUNT_SYNC_LOCK + promptId;
        String uuid = UUID.randomUUID().toString();
        Boolean locked = tryLock(lockKey, uuid, 30);
        if(!Boolean.TRUE.equals(locked)) {
            return false;
        }

        try {
            List<Long> deltas = getLiveCounts(promptId);
            long viewDelta = deltas.get(0);
            long likeDelta = deltas.get(1);
            long favoriteDelta = deltas.get(2);
            long copyDelta = deltas.get(3);

            if (viewDelta == 0L && likeDelta == 0L && favoriteDelta == 0L && copyDelta == 0L) {
                return true;
            }
            promptMapper.incrementCounts(
                    promptId,
                    (int) viewDelta,
                    (int) likeDelta,
                    (int) favoriteDelta,
                    (int) copyDelta
            );
            deductLiveCounts(promptId, deltas);
            return true;
        } finally {
            stringRedisTemplate.execute(unlockScript, List.of(lockKey), uuid);
        }
    }
    //用lua脚本获取当前liveKEY的值
    @SuppressWarnings("unchecked")
    private List<Long> getLiveCounts(Long promptId) {
        List<String> keys = List.of(
                PROMPT_VIEW_COUNT + promptId,
                PROMPT_LIKE_COUNT + promptId,
                PROMPT_FAVORITE_COUNT + promptId,
                PROMPT_COPY_COUNT + promptId
        );
        List<Object> raw = stringRedisTemplate.execute(countSnapshotScript, keys);
        if(raw == null || raw.size() != 4) {
            return List.of(0L, 0L, 0L, 0L);
        }
        List<Long> result = new ArrayList<>(4);
        for(int i = 0; i < 4; i++) {
            result.add(parseNumber(raw.get(i)));
        }
        return result;
    }
    private long parseNumber(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
    //执行扣减的lua脚本
    @SuppressWarnings("unchecked")
    private void deductLiveCounts(Long promptId, List<Long> deltas) {
        List<String> keys = List.of(
                PROMPT_VIEW_COUNT + promptId,
                PROMPT_LIKE_COUNT + promptId,
                PROMPT_FAVORITE_COUNT + promptId,
                PROMPT_COPY_COUNT + promptId
        );
        List<String> args = deltas.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        stringRedisTemplate.execute(countDeductScript, keys, args.toArray());
    }

    @Override
    public void addDirtyPromptId(Long promptId) {
        stringRedisTemplate.opsForSet().add(PROMPT_COUNT_DIRTY_SET, promptId.toString());
        stringRedisTemplate.expire(PROMPT_COUNT_DIRTY_SET, jitter(CACHE_EXPIRE_1DAY), TimeUnit.SECONDS);
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
    public void updateHotRanking(Long promptId, String rankingType, double scoreDelta) {
        String key = switch(rankingType) {
            case "like" -> PROMPT_HOT_LIKE;
            case "view" -> PROMPT_HOT_VIEW;
            case "favorite" -> PROMPT_HOT_FAVORITE;
            case "copy" -> PROMPT_HOT_COPY;
            default -> null;
        };
        if(key != null) {
            stringRedisTemplate.opsForZSet().incrementScore(key, promptId.toString(), scoreDelta);
            stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_1DAY), TimeUnit.SECONDS);
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
        stringRedisTemplate.expire(SEARCH_HOT_KEYWORDS, jitter(CACHE_EXPIRE_7DAY), TimeUnit.SECONDS);
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
        stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_7DAY), TimeUnit.SECONDS);
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
        stringRedisTemplate.expire(key, jitter(CACHE_EXPIRE_7DAY), TimeUnit.SECONDS);
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
        redisTemplate.opsForValue().set(CATEGORY_LIST, categories, jitter(CACHE_EXPIRE_1HOUR), TimeUnit.SECONDS);
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
    //分布式锁的获取和释放
    @Override
    public Boolean tryLock(String lockKey, String requestId, long expireTime) {
        return stringRedisTemplate.opsForValue().setIfAbsent(lockKey, requestId, expireTime, TimeUnit.SECONDS);

    }

    //限流相关业务实现
    @Override
    public boolean trySearchAllowed(String identifier, long limit, long windowSeconds) {
        String key = RATE_LIMIT_SEARCH + identifier;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if(count == null) {
            return false;
        }
        if(count == 1L) {
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count <= limit;
    }

    @Override
    public boolean tryRecordCopyCount(String identifier, Long promptId, long windowSeconds) {
        String key = COPY_DEDUP +  identifier + ":" + promptId;
        Boolean isSuccess = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", windowSeconds, TimeUnit.SECONDS);

        return  Boolean.TRUE.equals(isSuccess);
    }


    private long jitter(long baseSeconds) {
        return baseSeconds + ThreadLocalRandom.current().nextInt(300);
    }

    private long parseOrZero(List<String> values, int index) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return 0L;
        }
        return Long.parseLong(values.get(index));
    }
}
