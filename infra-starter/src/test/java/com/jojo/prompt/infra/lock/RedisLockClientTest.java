package com.jojo.prompt.infra.lock;

import com.jojo.prompt.infra.PromptInfraProperties;
import com.jojo.prompt.infra.metrics.InfraMetrics;
import com.jojo.prompt.infra.support.RedisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RedisLockClient 集成测试。
 *
 * <p>依赖本机 Redis（默认 localhost:6379）。Redis 不可用时整个类跳过——
 * 这是显式假设而非静默通过：跳过时 surefire 报告可见 skipped 标记。</p>
 */
class RedisLockClientTest {

    private static StringRedisTemplate redis;

    private RedisLockClient client;

    @BeforeAll
    static void connect() {
        assumeTrue(RedisTestSupport.reachable("localhost", 6379),
                "local Redis (localhost:6379) not available, skipping lock integration tests");
        redis = RedisTestSupport.pooledTemplate("localhost", 6379);
    }

    @BeforeEach
    void setUp() {
        PromptInfraProperties props = new PromptInfraProperties();
        props.getLock().setDefaultLeaseMs(5_000L);
        client = new RedisLockClient(
                redis,
                script("lua/lock_acquire.lua"),
                script("lua/lock_release.lua"),
                script("lua/lock_renew.lua"),
                props,
                new InfraMetrics(null));
    }

    @AfterEach
    void tearDown() {
        redis.delete(List.of("lock:test-key", "lock:test-key2", "lock:contend"));
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        s.setResultType(Long.class);
        return s;
    }

    @Test
    void lockIsMutuallyExclusiveAcrossThreads() throws Exception {
        assertTrue(client.tryLock("test-key"));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> other = pool.submit(() -> client.tryLock("test-key"));
            assertFalse(other.get(), "另一个线程应无法获取已被持有的锁");
        } finally {
            pool.shutdownNow();
        }
        client.unlock("test-key");
        assertFalse(redis.hasKey("lock:test-key"), "释放后锁 key 应被删除");
    }

    @Test
    void lockIsReentrantWithinSameThread() {
        assertTrue(client.tryLock("test-key"));
        assertTrue(client.tryLock("test-key"), "同一线程重入应成功");
        assertTrue(client.isHeldByCurrentThread("test-key"));
        client.unlock("test-key");
        assertTrue(client.isHeldByCurrentThread("test-key"), "只释放一层后仍持有");
        client.unlock("test-key");
        assertFalse(client.isHeldByCurrentThread("test-key"));
        assertFalse(redis.hasKey("lock:test-key"));
    }

    @Test
    void unlockByNonOwnerIsRejected() throws Exception {
        assertTrue(client.tryLock("test-key"));
        java.util.concurrent.atomic.AtomicReference<Throwable> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                client.unlock("test-key");
            } catch (Throwable t) {
                captured.set(t);
            }
        });
        other.start();
        other.join();
        assertTrue(captured.get() instanceof IllegalStateException,
                "非持有线程释放应被拒绝，实际捕获: " + captured.get());
        assertTrue(redis.hasKey("lock:test-key"), "锁不应被非持有者误删");
        client.unlock("test-key");
    }

    @Test
    void watchdogKeepsLockAliveBeyondLease() throws Exception {
        PromptInfraProperties props = new PromptInfraProperties();
        props.getLock().setDefaultLeaseMs(300L);          // 租约 300ms
        props.getLock().setWatchdogEnabled(true);
        RedisLockClient shortLease = new RedisLockClient(
                redis,
                script("lua/lock_acquire.lua"),
                script("lua/lock_release.lua"),
                script("lua/lock_renew.lua"),
                props,
                new InfraMetrics(null));
        assertTrue(shortLease.tryLock("test-key2"));
        Thread.sleep(1_000L);                             // 超过租约 3 倍
        assertTrue(redis.hasKey("lock:test-key2"), "看门狗应续期使锁存活");
        shortLease.unlock("test-key2");
        assertFalse(redis.hasKey("lock:test-key2"));
    }

    @Test
    void lockExpiresWhenWatchdogDisabled() throws Exception {
        PromptInfraProperties props = new PromptInfraProperties();
        props.getLock().setDefaultLeaseMs(200L);
        props.getLock().setWatchdogEnabled(false);
        RedisLockClient shortLease = new RedisLockClient(
                redis,
                script("lua/lock_acquire.lua"),
                script("lua/lock_release.lua"),
                script("lua/lock_renew.lua"),
                props,
                new InfraMetrics(null));
        assertTrue(shortLease.tryLock("test-key2"));
        Thread.sleep(400L);
        assertFalse(redis.hasKey("lock:test-key2"), "禁用看门狗后锁应按租约过期");
    }

    @Test
    void executeWithLockFailsFastWhenBusy() throws Exception {
        assertTrue(client.tryLock("test-key"));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Exception> other = pool.submit(() -> {
                try {
                    client.executeWithLock("test-key", () -> "should-not-run");
                    return null;
                } catch (Exception ex) {
                    return ex;
                }
            });
            assertTrue(other.get() instanceof RedisLockClient.LockAcquireException);
        } finally {
            pool.shutdownNow();
        }
        client.unlock("test-key");
        String result = client.executeWithLock("test-key", () -> "ok");
        assertEquals("ok", result);
    }

    @Test
    void contendersAllFailWhileLockHeld() throws Exception {
        // 确定性设计：主线程全程持锁，20 个竞争者必须全部失败（互斥性的直接证据）
        assertTrue(client.tryLock("contend"));
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> client.tryLock("contend"));
        }
        List<Future<Boolean>> futures = pool.invokeAll(tasks);
        long acquired = futures.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).count();
        pool.shutdownNow();
        assertEquals(0, acquired, "持锁期间 20 个竞争者应全部失败");
        client.unlock("contend");
        assertFalse(redis.hasKey("lock:contend"));
    }
}
