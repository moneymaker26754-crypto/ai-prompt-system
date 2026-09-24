package com.jojo.prompt.infra.bloom;

import com.jojo.prompt.infra.PromptInfraProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 按 starter 全局配置（期望元素数/误判率）创建 RedisBloomFilter 的工厂。 */
public class RedisBloomFilterFactory {

    private final StringRedisTemplate stringRedisTemplate;
    private final PromptInfraProperties properties;

    public RedisBloomFilterFactory(StringRedisTemplate stringRedisTemplate,
                                   PromptInfraProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public RedisBloomFilter create(String key) {
        return RedisBloomFilter.create(
                stringRedisTemplate,
                key,
                properties.getBloom().getExpectedInsertions(),
                properties.getBloom().getFpp());
    }
}
