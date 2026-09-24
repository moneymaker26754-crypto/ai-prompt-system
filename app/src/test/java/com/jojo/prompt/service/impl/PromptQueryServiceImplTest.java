package com.jojo.prompt.service.impl;

import com.jojo.prompt.common.config.PromptBloomWarmer;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.converter.PromptConverter;
import com.jojo.prompt.infra.bloom.RedisBloomFilter;
import com.jojo.prompt.infra.lock.RedisLockClient;
import com.jojo.prompt.mapper.CategoryMapper;
import com.jojo.prompt.mapper.PromptMapper;
import com.jojo.prompt.service.PromptFavoriteService;
import com.jojo.prompt.service.PromptLikeService;
import com.jojo.prompt.service.RedisCacheService;
import com.jojo.prompt.service.SearchHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PromptQueryServiceImpl 缓存三防切片测试：
 * 布隆拦截不存在的 ID（防穿透）+ 未预热 fail-open + 互斥重建竞争路径。
 */
@ExtendWith(MockitoExtension.class)
class PromptQueryServiceImplTest {

    @Mock private PromptMapper promptMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private PromptLikeService promptLikeService;
    @Mock private PromptFavoriteService promptFavoriteService;
    @Mock private SearchHistoryService searchHistoryService;
    @Mock private PromptConverter promptConverter;
    @Mock private RedisCacheService redisCacheService;
    @Mock private PromptInteractionAssembler promptInteractionAssembler;
    @Mock private PromptPermissionService promptPermissionService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RedisLockClient redisLockClient;
    @Mock private RedisBloomFilter promptIdBloomFilter;
    @Mock private PromptBloomWarmer promptBloomWarmer;
    @org.mockito.Spy private PromptLocalCache promptLocalCache = new PromptLocalCache();

    @InjectMocks
    private PromptQueryServiceImpl service;

    @Test
    void bloomRejectsMissingIdWithoutHittingDb() {
        when(promptPermissionService.getCurrentUserIdOrNull()).thenReturn(null);
        when(redisCacheService.isPromptNullCache(999L)).thenReturn(false);
        when(redisCacheService.getPromptDetailCache(999L)).thenReturn(null);
        when(redisLockClient.tryLock(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(false);
        when(promptBloomWarmer.isWarmed()).thenReturn(true);
        when(promptIdBloomFilter.mightContain("999")).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.queryPromptById(999L));

        verify(promptMapper, never()).selectById(999L);
    }

    @Test
    void bloomSkippedBeforeWarmUpIsFailOpen() {
        when(promptPermissionService.getCurrentUserIdOrNull()).thenReturn(null);
        when(redisCacheService.isPromptNullCache(1L)).thenReturn(false);
        when(redisCacheService.getPromptDetailCache(1L)).thenReturn(null);
        when(redisLockClient.tryLock(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(false);
        when(promptBloomWarmer.isWarmed()).thenReturn(false);
        when(promptMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.queryPromptById(1L));

        verify(promptMapper).selectById(1L);
        verify(promptIdBloomFilter, never()).mightContain("1");
    }

    @Test
    void secondCacheCheckAfterWinningRebuildLockAvoidsDb() {
        com.jojo.prompt.dto.response.PromptVO cached = new com.jojo.prompt.dto.response.PromptVO();
        cached.setId(2L);
        cached.setUserId(10L);
        when(promptPermissionService.getCurrentUserIdOrNull()).thenReturn(null);
        when(redisCacheService.isPromptNullCache(2L)).thenReturn(false);
        when(redisCacheService.getPromptDetailCache(2L)).thenReturn(null, cached);
        when(redisLockClient.tryLock(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        // 互斥重建：持锁期间二次读缓存命中（另一个实例已重建完成）
        service.queryPromptById(2L);
        verify(promptMapper, never()).selectById(2L);
        verify(redisLockClient).unlock(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void localCacheHitSkipsRedisReadAndDb() {
        com.jojo.prompt.dto.response.PromptVO cached = new com.jojo.prompt.dto.response.PromptVO();
        cached.setId(3L);
        cached.setUserId(11L);
        promptLocalCache.put(3L, cached);

        when(promptPermissionService.getCurrentUserIdOrNull()).thenReturn(null);
        when(redisCacheService.isPromptNullCache(3L)).thenReturn(false);

        service.queryPromptById(3L);

        verify(redisCacheService, never()).getPromptDetailCache(3L);
        verify(promptMapper, never()).selectById(3L);
    }

    @Test
    void commandServiceInvalidationClearsLocalCache() {
        com.jojo.prompt.dto.response.PromptVO cached = new com.jojo.prompt.dto.response.PromptVO();
        cached.setId(4L);
        promptLocalCache.put(4L, cached);
        promptLocalCache.invalidate(4L);
        org.junit.jupiter.api.Assertions.assertNull(promptLocalCache.get(4L));
    }
}
