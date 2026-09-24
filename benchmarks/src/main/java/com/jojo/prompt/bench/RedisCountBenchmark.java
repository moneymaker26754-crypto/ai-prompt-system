package com.jojo.prompt.bench;

import com.jojo.prompt.infra.PromptInfraProperties;
import com.jojo.prompt.infra.ratelimit.SlidingWindowRateLimiter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Redis 计数相关微基准：主项目计数链路的各实现路径逐一对比。
 *
 * <p>场景对应关系（与 docs/benchmarks 报告一致）：</p>
 * <ul>
 *   <li>fixedWindowIncr：改造前的固定窗口限流判定（INCR+EXPIRE 稳态即单次 INCR）</li>
 *   <li>slidingWindowLua：starter 滑动窗口（ZSET+Lua 单脚本原子判定）</li>
 *   <li>incrPipelinedBatch50：pipeline 批量 INCR（50/批，参考值，当前计数路径为单条 INCR）</li>
 *   <li>zsetIncrementScore：热度榜 ZINCRBY（单成员）</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class RedisCountBenchmark {

    private StringRedisTemplate redis;
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    private static final String INCR_KEY = "bench:count:incr";
    private static final String ZSET_KEY = "bench:count:zset";
    private static final String RATE_KEY = "bench:rate:sw";

    @Setup
    public void setup() {
        String host = System.getProperty("bench.redis.host", "localhost");
        int port = Integer.parseInt(System.getProperty("bench.redis.port", "6379"));
        redis = BenchRedisSupport.pooledTemplate(host, port);
        redis.delete(List.of(INCR_KEY, ZSET_KEY, RATE_KEY));

        DefaultRedisScript<Long> slidingScript = new DefaultRedisScript<>();
        slidingScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/rate_limit_sliding_window.lua")));
        slidingScript.setResultType(Long.class);
        PromptInfraProperties props = new PromptInfraProperties();
        slidingWindowRateLimiter = new SlidingWindowRateLimiter(redis, slidingScript, props);
    }

    @TearDown
    public void teardown() {
        redis.delete(List.of(INCR_KEY, ZSET_KEY, RATE_KEY));
    }

    /** 固定窗口限流稳态判定：单次 INCR（改造前实现） */
    @Benchmark
    public long fixedWindowIncr() {
        Long count = redis.opsForValue().increment(INCR_KEY);
        return count == null ? -1 : count;
    }

    /** 滑动窗口限流判定：单条 Lua（ZREMRANGEBYSCORE+ZCARD+ZADD+PEXPIRE 原子完成） */
    @Benchmark
    public boolean slidingWindowLua() {
        return slidingWindowRateLimiter.tryAcquire(RATE_KEY, 1_000_000, 60);
    }

    /** pipeline 批量 INCR（50/批），摊薄到单条的参考吞吐 */
    @Benchmark
    public long incrPipelinedBatch50() {
        redis.executePipelined((RedisCallback<Object>) connection -> {
            for (int i = 0; i < 50; i++) {
                connection.incr(INCR_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        });
        return 50L;
    }

    /** 热度榜单成员 ZINCRBY */
    @Benchmark
    public double zsetIncrementScore() {
        Double score = redis.opsForZSet().incrementScore(
                ZSET_KEY, String.valueOf(ThreadLocalRandom.current().nextLong(1, 1501)), 1.0);
        return score == null ? -1 : score;
    }
}
