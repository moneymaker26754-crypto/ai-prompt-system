package com.jojo.prompt.infra.bloom;

import com.jojo.prompt.infra.support.RedisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 布隆过滤器测试：纯数学部分（m/k 推导、哈希确定性）不依赖 Redis，
 * 集成部分依赖本机 Redis（不可用时跳过）。
 */
class RedisBloomFilterTest {

    private static StringRedisTemplate redis;
    private RedisBloomFilter filter;

    @BeforeAll
    static void connect() {
        assumeTrue(RedisTestSupport.reachable("localhost", 6379),
                "local Redis (localhost:6379) not available, skipping bloom integration tests");
        redis = RedisTestSupport.pooledTemplate("localhost", 6379);
    }

    @BeforeEach
    void setUp() {
        filter = RedisBloomFilter.create(redis, "bloom:test:" + UUID.randomUUID(), 1_000L, 0.01d);
    }

    @AfterEach
    void tearDown() {
        filter.clear();
    }

    // ---- 纯数学 ----

    @Test
    void optimalSizesFollowTheory() {
        // n=1000, p=0.01 → m≈9586, k≈7（理论公式值）
        long m = RedisBloomFilter.optimalBitSize(1_000L, 0.01d);
        int k = RedisBloomFilter.optimalHashCount(1_000L, m);
        assertEquals(9586, m);
        assertEquals(7, k);
    }

    @Test
    void hashFunctionsAreDeterministic() {
        String value = "prompt:123";
        assertEquals(RedisBloomFilter.fnv1a64(value), RedisBloomFilter.fnv1a64(value));
        assertEquals(RedisBloomFilter.splitmix64(42L), RedisBloomFilter.splitmix64(42L));
    }

    @Test
    void invalidParametersRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> RedisBloomFilter.optimalBitSize(0, 0.01d));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> RedisBloomFilter.optimalBitSize(100, 1.5d));
    }

    // ---- 集成 ----

    @Test
    void addedElementsAlwaysContained() {
        for (int i = 0; i < 1_000; i++) {
            filter.add("item-" + i);
        }
        for (int i = 0; i < 1_000; i++) {
            assertTrue(filter.mightContain("item-" + i), "已加入元素不应漏报（无假阴性）");
        }
    }

    @Test
    void falsePositiveRateWithinTheoreticalBound() {
        for (int i = 0; i < 1_000; i++) {
            filter.add("item-" + i);
        }
        int samples = 10_000;
        int falsePositives = 0;
        for (int i = 0; i < samples; i++) {
            if (filter.mightContain("missing-" + i)) {
                falsePositives++;
            }
        }
        double measured = (double) falsePositives / samples;
        // 理论 p=0.01，实测允许 2 倍裕量（哈希离散与有限样本）
        assertTrue(measured <= 0.02,
                "实测误判率 " + String.format("%.4f", measured) + " 应 ≤ 理论值 2 倍裕量 0.02");
    }

    @Test
    void clearRemovesAllBits() {
        filter.add("some-value");
        assertTrue(filter.mightContain("some-value"));
        filter.clear();
        assertFalse(filter.mightContain("some-value"));
    }
}
