package com.jojo.prompt.infra;

import com.jojo.prompt.infra.bloom.RedisBloomFilter;
import com.jojo.prompt.infra.bloom.RedisBloomFilterFactory;
import com.jojo.prompt.infra.idempotent.IdempotentAspect;
import com.jojo.prompt.infra.lock.RedisLockClient;
import com.jojo.prompt.infra.metrics.InfraMetrics;
import com.jojo.prompt.infra.ratelimit.RateLimitAspect;
import com.jojo.prompt.infra.ratelimit.SlidingWindowRateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * prompt-infra-spring-boot-starter 自动装配入口。
 *
 * <p>装配条件：classpath 存在 StringRedisTemplate（即引入 data-redis）且
 * prompt.infra.enabled=true（默认开启）。业务方零配置即可获得分布式锁、
 * 滑动窗口限流、幂等、布隆过滤器等能力。</p>
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "prompt.infra", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PromptInfraProperties.class)
public class PromptInfraAutoConfiguration {

    // ---- 指标 ----

    @Bean
    @ConditionalOnMissingBean
    public InfraMetrics infraMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new InfraMetrics(meterRegistryProvider);
    }

    // ---- Lua 脚本 ----

    @Bean
    @ConditionalOnMissingBean(name = "promptInfraLockAcquireScript")
    public DefaultRedisScript<Long> promptInfraLockAcquireScript() {
        return longScript("lua/lock_acquire.lua");
    }

    @Bean
    @ConditionalOnMissingBean(name = "promptInfraLockReleaseScript")
    public DefaultRedisScript<Long> promptInfraLockReleaseScript() {
        return longScript("lua/lock_release.lua");
    }

    @Bean
    @ConditionalOnMissingBean(name = "promptInfraLockRenewScript")
    public DefaultRedisScript<Long> promptInfraLockRenewScript() {
        return longScript("lua/lock_renew.lua");
    }

    @Bean
    @ConditionalOnMissingBean(name = "promptInfraSlidingWindowScript")
    public DefaultRedisScript<Long> promptInfraSlidingWindowScript() {
        return longScript("lua/rate_limit_sliding_window.lua");
    }

    // ---- 组件 ----

    @Bean
    @ConditionalOnMissingBean
    public RedisLockClient redisLockClient(
            StringRedisTemplate stringRedisTemplate,
            PromptInfraProperties properties,
            InfraMetrics infraMetrics,
            RedisScript<Long> promptInfraLockAcquireScript,
            RedisScript<Long> promptInfraLockReleaseScript,
            RedisScript<Long> promptInfraLockRenewScript) {
        return new RedisLockClient(
                stringRedisTemplate,
                (DefaultRedisScript<Long>) promptInfraLockAcquireScript,
                (DefaultRedisScript<Long>) promptInfraLockReleaseScript,
                (DefaultRedisScript<Long>) promptInfraLockRenewScript,
                properties,
                infraMetrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public SlidingWindowRateLimiter slidingWindowRateLimiter(
            StringRedisTemplate stringRedisTemplate,
            PromptInfraProperties properties,
            RedisScript<Long> promptInfraSlidingWindowScript) {
        return new SlidingWindowRateLimiter(
                stringRedisTemplate,
                (DefaultRedisScript<Long>) promptInfraSlidingWindowScript,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(
            StringRedisTemplate stringRedisTemplate,
            PromptInfraProperties properties,
            InfraMetrics infraMetrics,
            ObjectProvider<com.jojo.prompt.infra.dimension.RequestDimension> dimensionProvider,
            RedisScript<Long> promptInfraSlidingWindowScript) {
        return new RateLimitAspect(
                stringRedisTemplate,
                (DefaultRedisScript<Long>) promptInfraSlidingWindowScript,
                properties,
                infraMetrics,
                dimensionProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(
            StringRedisTemplate stringRedisTemplate,
            PromptInfraProperties properties,
            InfraMetrics infraMetrics,
            ObjectProvider<com.jojo.prompt.infra.dimension.RequestDimension> dimensionProvider) {
        return new IdempotentAspect(stringRedisTemplate, properties, infraMetrics, dimensionProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisBloomFilterFactory redisBloomFilterFactory(
            StringRedisTemplate stringRedisTemplate,
            PromptInfraProperties properties) {
        return new RedisBloomFilterFactory(stringRedisTemplate, properties);
    }

    private static DefaultRedisScript<Long> longScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(Long.class);
        return script;
    }
}
