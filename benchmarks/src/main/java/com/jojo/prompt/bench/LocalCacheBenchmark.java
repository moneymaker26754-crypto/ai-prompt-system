package com.jojo.prompt.bench;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 两级缓存命中路径微基准：
 * <ul>
 *   <li>localCacheHit：Caffeine L1 命中（进程内）</li>
 *   <li>redisCacheHit：Redis L2 命中（loopback RTT + 反序列化）</li>
 * </ul>
 * 对应主项目 PromptQueryServiceImpl 的两级缓存设计。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class LocalCacheBenchmark {

    private Cache<Long, String> localCache;
    private StringRedisTemplate redis;
    private static final String KEY = "bench:l2";

    @Setup
    public void setup() {
        localCache = Caffeine.newBuilder().maximumSize(10_000).build();
        for (long i = 1; i <= 10_000; i++) {
            localCache.put(i, "v" + i);
        }
        redis = BenchRedisSupport.pooledTemplate(
                System.getProperty("bench.redis.host", "localhost"),
                Integer.parseInt(System.getProperty("bench.redis.port", "6379")));
        redis.opsForValue().set(KEY, "value");
    }

    @Benchmark
    public String localCacheHit() {
        return localCache.getIfPresent(ThreadLocalRandom.current().nextLong(1, 10_001));
    }

    @Benchmark
    public String redisCacheHit() {
        return redis.opsForValue().get(KEY);
    }
}
