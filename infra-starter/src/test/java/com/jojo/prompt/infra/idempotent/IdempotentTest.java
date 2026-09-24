package com.jojo.prompt.infra.idempotent;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 幂等切面集成测试（真实 Redis，不可用时显式跳过）。
 */
@SpringBootTest(
        classes = IdempotentTest.Config.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
        })
class IdempotentTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Config {
        @Bean
        ConfirmService confirmService() {
            return new ConfirmService();
        }
    }

    static class ConfirmService {
        final AtomicInteger executed = new AtomicInteger();

        @Idempotent(key = "confirm", expr = "#recordId")
        public String confirm(String recordId) {
            executed.incrementAndGet();
            return "ok:" + recordId;
        }

        @Idempotent(key = "confirm", expr = "#recordId")
        public String confirmThenFail(String recordId) {
            throw new IllegalStateException("business failed");
        }
    }

    @Autowired
    private ConfirmService service;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeAll
    static void checkRedis() {
        StringRedisTemplate probe = new StringRedisTemplate(new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6379)));
        probe.afterPropertiesSet();
        boolean reachable;
        try {
            probe.opsForValue().set("prompt:infra:idem:ping", "1", Duration.ofSeconds(5));
            reachable = true;
        } catch (Exception ex) {
            reachable = false;
        }
        assumeTrue(reachable, "local Redis (localhost:6379) not available, skipping idempotent tests");
    }

    @AfterEach
    void cleanKeys() {
        redis.delete(java.util.List.of(
                "prompt:infra:idem:confirm:r1",
                "prompt:infra:idem:confirm:r2",
                "prompt:infra:idem:confirm:r3"));
    }

    @Test
    void duplicateRequestExecutesBusinessOnlyOnce() {
        assertEquals("ok:r1", service.confirm("r1"));
        assertThrows(IdempotentConflictException.class, () -> service.confirm("r1"),
                "同一幂等键的重复请求应被拦截");
        assertEquals(1, service.executed.get(), "业务逻辑应只执行一次");
    }

    @Test
    void differentKeysAreIndependent() {
        service.confirm("r1");
        assertEquals("ok:r2", service.confirm("r2"));
        assertThrows(IdempotentConflictException.class, () -> service.confirm("r1"));
        assertEquals(2, service.executed.get());
    }

    @Test
    void businessFailureReleasesPlaceholderForRetry() {
        assertThrows(IllegalStateException.class, () -> service.confirmThenFail("r3"),
                "业务异常应向上抛出");
        // 占位已释放：相同 key 可再次尝试（这里用成功方法验证占位不残留）
        service.confirm("r3");
        assertThrows(IdempotentConflictException.class, () -> service.confirm("r3"));
    }
}
