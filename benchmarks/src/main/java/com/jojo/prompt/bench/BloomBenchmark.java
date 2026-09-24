package com.jojo.prompt.bench;

import com.jojo.prompt.infra.bloom.RedisBloomFilter;
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
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 布隆过滤器微基准：pipeline 批量位操作下的 add / mightContain 吞吐
 * （k 次 SETBIT/GETBIT 合并为一次 RTT）。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class BloomBenchmark {

    private RedisBloomFilter filter;

    @Setup
    public void setup() {
        String host = System.getProperty("bench.redis.host", "localhost");
        int port = Integer.parseInt(System.getProperty("bench.redis.port", "6379"));
        StringRedisTemplate redis = BenchRedisSupport.pooledTemplate(host, port);
        // 与主项目一致：10 万元素、1% 误判率 → m≈958,506 bit，k=7
        filter = RedisBloomFilter.create(redis, "bench:bloom", 100_000L, 0.01d);
        for (int i = 0; i < 10_000; i++) {
            filter.add("item-" + i);
        }
    }

    @Benchmark
    public boolean mightContain() {
        return filter.mightContain("item-" + ThreadLocalRandom.current().nextInt(10_000));
    }

    @Benchmark
    public void add() {
        filter.add("item-" + ThreadLocalRandom.current().nextInt(10_000));
    }
}
