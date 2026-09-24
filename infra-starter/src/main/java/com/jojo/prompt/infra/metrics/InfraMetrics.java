package com.jojo.prompt.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 基础设施组件指标门面。
 *
 * <p>业务方引入 actuator 时自动挂载到真实 MeterRegistry（Prometheus 可抓取）；
 * 未引入 actuator 时降级为内存 SimpleMeterRegistry，组件代码无需判空。</p>
 */
public class InfraMetrics {

    private final MeterRegistry registry;

    public InfraMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.registry = meterRegistryProvider == null
                ? new SimpleMeterRegistry()
                : meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
    }

    public MeterRegistry registry() {
        return registry;
    }

    // ---- 分布式锁 ----
    public Counter lockAcquireAttempts() {
        return Counter.builder("prompt.infra.lock.acquire.attempts")
                .description("锁获取尝试次数")
                .register(registry);
    }

    public Counter lockAcquireFailures() {
        return Counter.builder("prompt.infra.lock.acquire.failures")
                .description("锁获取失败次数（锁被占用）")
                .register(registry);
    }

    // ---- 限流 ----
    public Counter rateLimitAllowed() {
        return Counter.builder("prompt.infra.rate_limit.allowed")
                .description("限流放行次数")
                .register(registry);
    }

    public Counter rateLimitRejected() {
        return Counter.builder("prompt.infra.rate_limit.rejected")
                .description("限流拒绝次数")
                .register(registry);
    }

    // ---- 幂等 ----
    public Counter idempotentConflicts() {
        return Counter.builder("prompt.infra.idempotent.conflicts")
                .description("幂等重复请求拦截次数")
                .register(registry);
    }

    // ---- 布隆过滤器 ----
    public Counter bloomMightContainCalls() {
        return Counter.builder("prompt.infra.bloom.might_contain.calls")
                .description("布隆存在性判定次数")
                .register(registry);
    }

    public Counter bloomMightContainHits() {
        return Counter.builder("prompt.infra.bloom.might_contain.hits")
                .description("布隆判定存在次数（含误判）")
                .register(registry);
    }
}
