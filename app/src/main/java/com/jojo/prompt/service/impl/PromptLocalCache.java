package com.jojo.prompt.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.jojo.prompt.dto.response.PromptVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Prompt 详情本地一级缓存（Caffeine，L1），与 Redis（L2）构成两级缓存。
 *
 * <p>动机（来自拐点实验）：详情缓存命中路径每请求仍要走 4 次 Redis RTT
 * （读缓存 + 计数 INCR + ZINCRBY + 成员判定），Lettuce 池 8 连接在 100+
 * 并发时先于 Druid/CPU 饱和。L1 命中时这些 RTT 全部省去。</p>
 *
 * <p>一致性设计：</p>
 * <ol>
 *   <li><b>只缓存基础 VO</b>：计数/点赞收藏状态每次命中仍实时合并
 *       （mergeRedisCountsToVO），60s 内内容/标题可能有滞后；</li>
 *   <li><b>写路径失效</b>：更新/删除 Prompt 时显式 invalidate（与 Redis 缓存删除同点）；</li>
 *   <li><b>容量 + TTL + 统计</b>：maximumSize 500、expireAfterWrite 60s、
 *       recordStats 供指标与压测观察命中率。</li>
 * </ol>
 */
@Slf4j
@Component
public class PromptLocalCache {

    private final Cache<Long, PromptVO> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .recordStats()
            .build();

    public PromptVO get(Long promptId) {
        return cache.getIfPresent(promptId);
    }

    public void put(Long promptId, PromptVO vo) {
        if (vo != null) {
            cache.put(promptId, vo);
        }
    }

    public void invalidate(Long promptId) {
        cache.invalidate(promptId);
    }

    /** 命中率快照（供观测/压测报告） */
    public CacheStats stats() {
        return cache.stats();
    }

    public double hitRate() {
        return cache.stats().hitRate();
    }
}
