package com.jojo.prompt.bench;

import com.jojo.prompt.infra.PromptInfraProperties;
import com.jojo.prompt.infra.lock.RedisLockClient;
import com.jojo.prompt.infra.metrics.InfraMetrics;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁微基准：无竞争路径吞吐 + 4 线程竞争下的吞吐。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class RedisLockBenchmark {

    private RedisLockClient client;
    private static final String KEY = "bench:lock";

    @Setup
    public void setup() {
        String host = System.getProperty("bench.redis.host", "localhost");
        int port = Integer.parseInt(System.getProperty("bench.redis.port", "6379"));
        StringRedisTemplate redis = BenchRedisSupport.pooledTemplate(host, port);

        PromptInfraProperties props = new PromptInfraProperties();
        props.getLock().setDefaultLeaseMs(30_000L);
        props.getLock().setWatchdogEnabled(false); // 基准中关闭续期线程，测纯锁路径

        client = new RedisLockClient(
                redis,
                script("lua/lock_acquire.lua"),
                script("lua/lock_release.lua"),
                script("lua/lock_renew.lua"),
                props,
                new InfraMetrics(null));
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        s.setResultType(Long.class);
        return s;
    }

    /** 无竞争：单线程获取+释放 */
    @Benchmark
    public boolean uncontendedLockUnlock() {
        boolean ok = client.tryLock(KEY, 30_000);
        if (ok) {
            client.unlock(KEY);
        }
        return ok;
    }

    /** 4 线程竞争同一把锁：整个组的吞吐（成功获取者立刻释放） */
    @Benchmark
    @Group("contended")
    @GroupThreads(4)
    public boolean contendedAcquireRelease() {
        boolean ok = client.tryLock(KEY, 30_000);
        if (ok) {
            client.unlock(KEY);
        }
        return ok;
    }
}
