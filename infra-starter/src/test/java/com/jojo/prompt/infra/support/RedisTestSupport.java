package com.jojo.prompt.infra.support;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 集成测试的 Redis 连接工具。
 *
 * <p>统一使用连接池：pipeline 类测试会高频开闭连接，Windows 上
 * 非池化连接易触发临时端口耗尽（"Address already in use"）。</p>
 */
public final class RedisTestSupport {

    private RedisTestSupport() {
    }

    public static LettuceConnectionFactory pooledFactory(String host, int port) {
        LettucePoolingClientConfiguration poolConfig =
                LettucePoolingClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(3))
                        .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, port), poolConfig);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    public static StringRedisTemplate pooledTemplate(String host, int port) {
        StringRedisTemplate template = new StringRedisTemplate(pooledFactory(host, port));
        template.afterPropertiesSet();
        return template;
    }

    /** 探测 Redis 是否可达；用于 assumeTrue（不可达时显式跳过集成用例） */
    public static boolean reachable(String host, int port) {
        StringRedisTemplate probe = pooledTemplate(host, port);
        try {
            probe.opsForValue().set("prompt:infra:test:ping", "1", Duration.ofSeconds(5));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
