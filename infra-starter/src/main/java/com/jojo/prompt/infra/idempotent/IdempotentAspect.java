package com.jojo.prompt.infra.idempotent;

import com.jojo.prompt.infra.PromptInfraProperties;
import com.jojo.prompt.infra.dimension.RequestDimension;
import com.jojo.prompt.infra.metrics.InfraMetrics;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 幂等切面：SETNX 占位 + 失败释放 + 成功保留。
 *
 * <p>Redis 不可用时 fail-open（放行并告警），保证核心链路可用性，
 * 与限流切面策略一致；异常计数通过 Micrometer 暴露。</p>
 */
@Slf4j
@Aspect
public class IdempotentAspect {

    private static final String KEY_PREFIX = "prompt:infra:idem:";

    private final StringRedisTemplate stringRedisTemplate;
    private final PromptInfraProperties properties;
    private final InfraMetrics metrics;
    private final ObjectProvider<RequestDimension> dimensionProvider;
    private final BeanFactory beanFactory;
    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    public IdempotentAspect(StringRedisTemplate stringRedisTemplate,
                            PromptInfraProperties properties,
                            InfraMetrics metrics,
                            ObjectProvider<RequestDimension> dimensionProvider,
                            BeanFactory beanFactory) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.metrics = metrics;
        this.dimensionProvider = dimensionProvider;
        this.beanFactory = beanFactory;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String key = buildKey(joinPoint, idempotent);
        long ttlSeconds = idempotent.ttlSeconds() > 0
                ? idempotent.ttlSeconds()
                : properties.getIdempotent().getDefaultTtlSeconds();
        boolean placed;
        try {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
            placed = Boolean.TRUE.equals(ok);
        } catch (Exception ex) {
            log.error("idempotent placeholder failed, key={}, failOpen=true", key, ex);
            placed = true; // Redis 故障：放行，可用性优先
        }
        if (!placed) {
            metrics.idempotentConflicts().increment();
            log.warn("duplicate request rejected, key={}", key);
            throw new IdempotentConflictException(idempotent.message());
        }
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            // 业务失败：释放占位，允许客户端重试
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception delEx) {
                log.warn("release idempotent placeholder failed, key={}", key, delEx);
            }
            throw ex;
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        String prefix = idempotent.key().isEmpty()
                ? joinPoint.getSignature().toShortString()
                : idempotent.key();
        String dimension;
        if (!idempotent.expr().isEmpty()) {
            dimension = resolveExpr(joinPoint, idempotent.expr());
        } else {
            RequestDimension requestDimension = dimensionProvider.getIfAvailable();
            String user = requestDimension == null ? null : requestDimension.currentUser();
            dimension = (user == null || user.isBlank()) ? "anonymous" : user;
        }
        return KEY_PREFIX + prefix + ":" + dimension;
    }

    private String resolveExpr(ProceedingJoinPoint joinPoint, String expr) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (beanFactory != null) {
            context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        }
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
