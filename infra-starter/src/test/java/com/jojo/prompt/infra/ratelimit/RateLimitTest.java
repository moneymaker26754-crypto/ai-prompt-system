package com.jojo.prompt.infra.ratelimit;

import com.jojo.prompt.infra.dimension.RequestDimension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 滑动窗口限流集成测试（真实 Redis，localhost:6379；不可用时显式跳过）。
 *
 * <p>IP/用户维度通过测试用 {@link RequestDimension}（ThreadLocal 驱动）模拟，
 * starter 本身不依赖 servlet/security，符合 SPI 设计。</p>
 */
@SpringBootTest(
        classes = RateLimitTest.Config.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
        })
class RateLimitTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Config {
        @Bean
        RateLimitedService rateLimitedService() {
            return new RateLimitedService();
        }

        @Bean
        RequestDimension requestDimension() {
            return new ThreadLocalRequestDimension();
        }
    }

    /** 测试维度实现：线程内可设置的 IP/用户 */
    static class ThreadLocalRequestDimension implements RequestDimension {
        static final ThreadLocal<String> IP = new ThreadLocal<>();
        static final ThreadLocal<String> USER = new ThreadLocal<>();

        @Override
        public String currentIp() {
            return IP.get();
        }

        @Override
        public String currentUser() {
            return USER.get();
        }
    }

    static class RateLimitedService {
        final AtomicInteger calls = new AtomicInteger();

        @RateLimit(limit = 3, windowSeconds = 60, key = "svc", dim = RateLimit.Dimension.EXPR, expr = "#tag")
        public String call(String tag) {
            calls.incrementAndGet();
            return tag;
        }

        @RateLimit(limit = 2, windowSeconds = 60, key = "ip-svc", dim = RateLimit.Dimension.IP)
        public String callByIp() {
            calls.incrementAndGet();
            return "ip";
        }
    }

    @Autowired
    private RateLimitedService service;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeAll
    static void checkRedis() {
        StringRedisTemplate probe = new StringRedisTemplate(new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6379)));
        probe.afterPropertiesSet();
        boolean reachable;
        try {
            probe.opsForValue().set("prompt:infra:rate:ping", "1", Duration.ofSeconds(5));
            reachable = true;
        } catch (Exception ex) {
            reachable = false;
        }
        assumeTrue(reachable, "local Redis (localhost:6379) not available, skipping rate limit tests");
    }

    @AfterEach
    void cleanKeys() {
        ThreadLocalRequestDimension.IP.remove();
        ThreadLocalRequestDimension.USER.remove();
        redis.delete(List.of(
                "prompt:infra:rate:svc:EXPR:t1",
                "prompt:infra:rate:svc:EXPR:t2",
                "prompt:infra:rate:svc:EXPR:t3",
                "prompt:infra:rate:ip-svc:IP:10.0.0.1",
                "prompt:infra:rate:ip-svc:IP:10.0.0.2"));
    }

    @Test
    void allowsUpToLimitThenRejects() {
        service.call("t1");
        service.call("t1");
        service.call("t1");
        assertThrows(RateLimitExceededException.class, () -> service.call("t1"),
                "第 4 次请求应被滑动窗口拒绝");
        assertEquals(3, service.calls.get());
    }

    @Test
    void dimensionsAreIsolated() {
        service.call("t1");
        service.call("t1");
        service.call("t1");
        // t2 是另一个维度，不受 t1 的 3 次影响
        service.call("t2");
        service.call("t2");
        service.call("t2");
        assertThrows(RateLimitExceededException.class, () -> service.call("t2"));
        assertEquals(6, service.calls.get());
    }

    @Test
    void concurrentBurstIsCountedExactly() throws Exception {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    service.call("t3");
                    return true;
                } catch (RateLimitExceededException ex) {
                    rejected.incrementAndGet();
                    return false;
                }
            });
        }
        List<Future<Boolean>> futures = pool.invokeAll(tasks);
        long success = futures.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).count();
        pool.shutdownNow();
        assertEquals(3, success, "50 并发请求应恰好放行 3 个（Lua 原子计数）");
        assertEquals(47, rejected.get());
    }

    @Test
    void ipDimensionIsResolvedViaSpiAndIsolated() {
        ThreadLocalRequestDimension.IP.set("10.0.0.1");
        service.callByIp();
        service.callByIp();
        assertThrows(RateLimitExceededException.class, service::callByIp,
                "同一 IP 超出限流阈值应被拒绝");
        ThreadLocalRequestDimension.IP.set("10.0.0.2");
        service.callByIp();
    }
}
