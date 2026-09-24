package com.jojo.prompt.infra.ratelimit;

import com.jojo.prompt.infra.PromptInfraProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;

/**
 * 编程式滑动窗口限流器（与 @RateLimit 切面共用同一 Lua 脚本）。
 *
 * <p>给不需要 AOP 的场景使用：如主项目 service 层已有的
 * {@code trySearchAllowed / tryLoginAllowed} 调用点。</p>
 */
public class SlidingWindowRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> slidingWindowScript;
    private final PromptInfraProperties properties;

    public SlidingWindowRateLimiter(StringRedisTemplate stringRedisTemplate,
                                    DefaultRedisScript<Long> slidingWindowScript,
                                    PromptInfraProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.slidingWindowScript = slidingWindowScript;
        this.properties = properties;
    }

    /**
     * 尝试获取一次配额。
     *
     * @param key           限流 key（如 rate:limit:search:127.0.0.1）
     * @param limit         窗口内允许次数
     * @param windowSeconds 窗口长度（秒）
     * @return true=放行
     */
    public boolean tryAcquire(String key, long limit, long windowSeconds) {
        long windowMs = windowSeconds * 1000L;
        long now = System.currentTimeMillis();
        String member = now + "-" + UUID.randomUUID();
        try {
            Long result = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(key),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    String.valueOf(now),
                    member);
            return result != null && result == 1L;
        } catch (Exception ex) {
            // 与切面策略一致：Redis 故障按 fail-open 处理
            return properties.getRateLimit().isFailOpen();
        }
    }
}
