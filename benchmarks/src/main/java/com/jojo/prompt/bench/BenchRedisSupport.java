package com.jojo.prompt.bench;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/** 基准用 Redis 连接工具：统一连接池，避免 Windows 临时端口耗尽。 */
public final class BenchRedisSupport {

    private BenchRedisSupport() {
    }

    public static StringRedisTemplate pooledTemplate(String host, int port) {
        LettucePoolingClientConfiguration poolConfig =
                LettucePoolingClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(3))
                        .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, port), poolConfig);
        factory.afterPropertiesSet();
        factory.start();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }
}
