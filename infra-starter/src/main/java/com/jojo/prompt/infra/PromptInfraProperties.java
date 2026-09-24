package com.jojo.prompt.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * prompt-infra-spring-boot-starter 配置属性。
 *
 * <p>默认全部开启（enabled=true, matchIfMissing），业务方可通过
 * prompt.infra.* 覆盖；关闭某项能力时对应组件不再装配。</p>
 */
@ConfigurationProperties(prefix = "prompt.infra")
public class PromptInfraProperties {

    private boolean enabled = true;

    private Lock lock = new Lock();
    private RateLimit rateLimit = new RateLimit();
    private Idempotent idempotent = new Idempotent();
    private Bloom bloom = new Bloom();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Lock getLock() {
        return lock;
    }

    public void setLock(Lock lock) {
        this.lock = lock;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Idempotent getIdempotent() {
        return idempotent;
    }

    public void setIdempotent(Idempotent idempotent) {
        this.idempotent = idempotent;
    }

    public Bloom getBloom() {
        return bloom;
    }

    public void setBloom(Bloom bloom) {
        this.bloom = bloom;
    }

    /** 分布式锁 */
    public static class Lock {
        /** 默认租约时长（毫秒），业务可逐次覆盖 */
        private long defaultLeaseMs = 30_000L;
        /** 是否启用看门狗自动续期 */
        private boolean watchdogEnabled = true;
        /** 看门狗续期间隔 = lease / renewalFactor */
        private int renewalFactor = 3;

        public long getDefaultLeaseMs() {
            return defaultLeaseMs;
        }

        public void setDefaultLeaseMs(long defaultLeaseMs) {
            this.defaultLeaseMs = defaultLeaseMs;
        }

        public boolean isWatchdogEnabled() {
            return watchdogEnabled;
        }

        public void setWatchdogEnabled(boolean watchdogEnabled) {
            this.watchdogEnabled = watchdogEnabled;
        }

        public int getRenewalFactor() {
            return renewalFactor;
        }

        public void setRenewalFactor(int renewalFactor) {
            this.renewalFactor = renewalFactor;
        }
    }

    /** 限流 */
    public static class RateLimit {
        /** Redis 异常时放行（可用性优先）还是拒绝（严格优先） */
        private boolean failOpen = true;
        /** 单次 Lua 判定允许的时钟偏移补偿（毫秒） */
        private long clockSkewMs = 50L;

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }

        public long getClockSkewMs() {
            return clockSkewMs;
        }

        public void setClockSkewMs(long clockSkewMs) {
            this.clockSkewMs = clockSkewMs;
        }
    }

    /** 幂等 */
    public static class Idempotent {
        private long defaultTtlSeconds = 600L;

        public long getDefaultTtlSeconds() {
            return defaultTtlSeconds;
        }

        public void setDefaultTtlSeconds(long defaultTtlSeconds) {
            this.defaultTtlSeconds = defaultTtlSeconds;
        }
    }

    /** 布隆过滤器 */
    public static class Bloom {
        private long expectedInsertions = 100_000L;
        private double fpp = 0.01d;

        public long getExpectedInsertions() {
            return expectedInsertions;
        }

        public void setExpectedInsertions(long expectedInsertions) {
            this.expectedInsertions = expectedInsertions;
        }

        public double getFpp() {
            return fpp;
        }

        public void setFpp(double fpp) {
            this.fpp = fpp;
        }
    }
}
