package com.jojo.prompt.infra.bloom;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis bitmap 的布隆过滤器。
 *
 * <p>工程决策：</p>
 * <ol>
 *   <li><b>容量/误判率可配</b>：由期望元素数 n 与误判率 p 推导位数组长度
 *       m = -n·ln(p)/(ln2)² 与哈希函数个数 k = (m/n)·ln2。</li>
 *   <li><b>双哈希派生</b>：k 个哈希位由一次 FNV-1a 64 与一次 SplitMix64
 *       按 Kirsch-Mitzenmacher g_i = h1 + i·h2 派生，避免引入 Guava 依赖。</li>
 *   <li><b>Pipeline 批处理</b>：add/mightContain 的 k 次位操作走一次
 *       Redis pipeline，RTT 从 k 次降为 1 次（这是本组件主要性能收益）。</li>
 * </ol>
 */
public class RedisBloomFilter {

    private static final int BITS_PER_BYTE = 8;

    private final StringRedisTemplate stringRedisTemplate;
    private final String key;
    private final long bitSize;
    private final int hashCount;

    private RedisBloomFilter(StringRedisTemplate stringRedisTemplate, String key,
                             long bitSize, int hashCount) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.key = key;
        this.bitSize = bitSize;
        this.hashCount = hashCount;
    }

    /**
     * 按期望元素数与误判率创建过滤器。
     *
     * @param expectedInsertions 期望元素数 n
     * @param fpp                目标误判率 p（0 &lt; p &lt; 1）
     */
    public static RedisBloomFilter create(StringRedisTemplate stringRedisTemplate,
                                          String key, long expectedInsertions, double fpp) {
        long m = optimalBitSize(expectedInsertions, fpp);
        int k = optimalHashCount(expectedInsertions, m);
        return new RedisBloomFilter(stringRedisTemplate, key, m, k);
    }

    /** m = -n·ln(p) / (ln2)² */
    static long optimalBitSize(long n, double p) {
        if (n <= 0 || p <= 0 || p >= 1) {
            throw new IllegalArgumentException("expectedInsertions > 0 and 0 < fpp < 1 required");
        }
        return (long) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    /** k = (m/n)·ln2 */
    static int optimalHashCount(long n, long m) {
        return Math.max(1, (int) Math.round(((double) m / n) * Math.log(2)));
    }

    public void add(String value) {
        List<Object> results = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (long bit : hashBits(value)) {
                connection.setBit(key.getBytes(StandardCharsets.UTF_8), bit, true);
            }
            return null;
        });
        // 触发 pipeline 执行
        results.size();
    }

    public boolean mightContain(String value) {
        long[] bits = hashBits(value);
        List<Object> results = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (long bit : bits) {
                connection.getBit(key.getBytes(StandardCharsets.UTF_8), bit);
            }
            return null;
        });
        for (int i = 0; i < hashCount; i++) {
            Object r = results.get(i);
            boolean set = Boolean.TRUE.equals(r) || Long.valueOf(1L).equals(r);
            if (!set) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        stringRedisTemplate.delete(key);
    }

    public long bitSize() {
        return bitSize;
    }

    public int hashCount() {
        return hashCount;
    }

    /** 一次哈希派生出 k 个位下标（Kirsch-Mitzenmacher） */
    private long[] hashBits(String value) {
        long h1 = fnv1a64(value);
        long h2 = splitmix64(h1 ^ 0x9E3779B97F4A7C15L);
        long[] bits = new long[hashCount];
        for (int i = 0; i < hashCount; i++) {
            long combined = h1 + i * h2;
            bits[i] = Math.floorMod(combined, bitSize);
        }
        return bits;
    }

    /** FNV-1a 64 位哈希 */
    static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** SplitMix64 混淆：把低位熵扩散到高位，避免双哈希相关性 */
    static long splitmix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** 提供给指标/监控的配置快照 */
    public Map<String, Object> describe() {
        return Map.of(
                "key", key,
                "bitSize", bitSize,
                "hashCount", hashCount,
                "memoryBytesApprox", bitSize / BITS_PER_BYTE);
    }
}
