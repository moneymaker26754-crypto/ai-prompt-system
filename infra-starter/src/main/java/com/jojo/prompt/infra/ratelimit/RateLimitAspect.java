package com.jojo.prompt.infra.ratelimit;

import com.jojo.prompt.infra.PromptInfraProperties;
import com.jojo.prompt.infra.dimension.RequestDimension;
import com.jojo.prompt.infra.metrics.InfraMetrics;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * 滑动窗口限流切面。
 *
 * <p>判定逻辑在 Redis 单条 Lua 内原子完成，切面只负责：解析维度 key →
 * 执行脚本 → 失败策略。IP/用户维度通过 {@link RequestDimension} SPI 获取，
 * starter 本身不依赖 servlet / security。Redis 故障时按
 * {@code prompt.infra.rate-limit.fail-open} 决定放行（可用性优先，默认）
 * 或拒绝（严格模式）。</p>
 */
@Slf4j
@Aspect
public class RateLimitAspect {

    private static final String KEY_PREFIX = "prompt:infra:rate:";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> slidingWindowScript;
    private final PromptInfraProperties properties;
    private final InfraMetrics metrics;
    private final ObjectProvider<RequestDimension> dimensionProvider;
    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    public RateLimitAspect(StringRedisTemplate stringRedisTemplate,
                           DefaultRedisScript<Long> slidingWindowScript,
                           PromptInfraProperties properties,
                           InfraMetrics metrics,
                           ObjectProvider<RequestDimension> dimensionProvider) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.slidingWindowScript = slidingWindowScript;
        this.properties = properties;
        this.metrics = metrics;
        this.dimensionProvider = dimensionProvider;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = buildKey(joinPoint, rateLimit);
        long windowMs = rateLimit.windowSeconds() * 1000L;
        long now = System.currentTimeMillis();
        String member = now + "-" + UUID.randomUUID();
        boolean allowed;
        try {
            Long result = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(key),
                    String.valueOf(windowMs),
                    String.valueOf(rateLimit.limit()),
                    String.valueOf(now),
                    member);
            allowed = result != null && result == 1L;
        } catch (Exception ex) {
            log.error("sliding window rate limit check failed, key={}, failOpen={}",
                    key, properties.getRateLimit().isFailOpen(), ex);
            allowed = properties.getRateLimit().isFailOpen();
        }
        if (!allowed) {
            metrics.rateLimitRejected().increment();
            log.warn("rate limit exceeded, key={}, limit={}, windowMs={}",
                    key, rateLimit.limit(), windowMs);
            throw new RateLimitExceededException(rateLimit.message());
        }
        metrics.rateLimitAllowed().increment();
        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String dimension = switch (rateLimit.dim()) {
            case IP -> resolveIp();
            case USER -> resolveUser();
            case EXPR -> resolveExpr(joinPoint, rateLimit.expr());
        };
        String prefix = rateLimit.key().isEmpty()
                ? joinPoint.getSignature().toShortString()
                : rateLimit.key();
        return KEY_PREFIX + prefix + ":" + rateLimit.dim().name() + ":" + dimension;
    }

    private String resolveIp() {
        RequestDimension dimension = dimensionProvider.getIfAvailable();
        String ip = dimension == null ? null : dimension.currentIp();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private String resolveUser() {
        RequestDimension dimension = dimensionProvider.getIfAvailable();
        String user = dimension == null ? null : dimension.currentUser();
        if (user != null && !user.isBlank()) {
            return user;
        }
        return resolveIp();
    }

    private String resolveExpr(ProceedingJoinPoint joinPoint, String expr) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        Object value = spelParser.parseExpression(expr).getValue(context);
        return value == null ? "null" : String.valueOf(value);
    }
}
