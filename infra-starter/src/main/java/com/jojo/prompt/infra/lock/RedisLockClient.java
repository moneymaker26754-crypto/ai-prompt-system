package com.jojo.prompt.infra.lock;

import com.jojo.prompt.infra.PromptInfraProperties;
import com.jojo.prompt.infra.metrics.InfraMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Redis Hash + Lua 的可重入分布式锁客户端。
 *
 * <p>设计要点（与简历可讲的工程决策一一对应）：</p>
 * <ol>
 *   <li><b>可重入</b>：HINCRBY 计数，同一线程重复获取不阻塞自身；token 按线程生成，
 *       同一线程重入时复用 token，避免计数与身份不一致。</li>
 *   <li><b>防误删</b>：释放/续期 Lua 先校验 token 再操作，杜绝「A 的锁被 B 释放」。</li>
 *   <li><b>WatchDog 续期</b>：业务持锁超过租约时由后台调度器以 lease/3 间隔续租，
 *       续期同样校验 token，锁被抢占后自动停止。</li>
 *   <li><b>原子性</b>：获取/释放/续期均为单条 Lua 脚本在 Redis 内原子执行。</li>
 * </ol>
 */
@Slf4j
public class RedisLockClient {

    private static final String LOCK_KEY_PREFIX = "lock:";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> acquireScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final DefaultRedisScript<Long> renewScript;
    private final PromptInfraProperties properties;
    private final InfraMetrics metrics;

    /** 看门狗调度器（daemon 单线程，续期任务轻量） */
    private final ScheduledExecutorService watchdog = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "prompt-infra-lock-watchdog-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    /** 当前线程持有的锁 key → 重入深度（线程隔离，天然线程安全） */
    private final ThreadLocal<Map<String, Integer>> heldLocks =
            ThreadLocal.withInitial(HashMap::new);

    /** 当前线程的持有者 token（线程内稳定，用于重入） */
    private final ThreadLocal<String> threadToken =
            ThreadLocal.withInitial(() -> "t" + Thread.currentThread().threadId() + "-" + UUID.randomUUID());

    /** 已启动续期的锁 key → 续期任务 */
    private final Map<String, ScheduledFuture<?>> renewTasks = new HashMap<>();

    public RedisLockClient(StringRedisTemplate stringRedisTemplate,
                           DefaultRedisScript<Long> acquireScript,
                           DefaultRedisScript<Long> releaseScript,
                           DefaultRedisScript<Long> renewScript,
                           PromptInfraProperties properties,
                           InfraMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.acquireScript = acquireScript;
        this.releaseScript = releaseScript;
        this.renewScript = renewScript;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 尝试获取锁（默认租约）。可重入：同一线程重复获取同一把锁直接成功。
     *
     * @return true=获取成功
     */
    public boolean tryLock(String key) {
        return tryLock(key, properties.getLock().getDefaultLeaseMs());
    }

    /**
     * 尝试获取锁。
     *
     * @param key     业务锁名（自动加 lock: 前缀）
     * @param leaseMs 租约毫秒
     * @return true=获取成功
     */
    public boolean tryLock(String key, long leaseMs) {
        String lockKey = LOCK_KEY_PREFIX + key;
        String token = threadToken.get();
        metrics.lockAcquireAttempts().increment();
        Long result = stringRedisTemplate.execute(
                acquireScript, List.of(lockKey), token, String.valueOf(leaseMs));
        boolean acquired = result != null && result == 1L;
        if (!acquired) {
            metrics.lockAcquireFailures().increment();
            return false;
        }
        Map<String, Integer> held = heldLocks.get();
        int depth = held.getOrDefault(key, 0);
        if (depth == 0) {
            startRenewalIfEnabled(lockKey, token, leaseMs);
        }
        held.put(key, depth + 1);
        return true;
    }

    /**
     * 释放锁（重入计数减一，归零后删除并停止续期）。
     *
     * @throws IllegalStateException 当前线程未持有该锁时
     */
    public void unlock(String key) {
        Map<String, Integer> held = heldLocks.get();
        Integer depth = held.get(key);
        if (depth == null || depth <= 0) {
            throw new IllegalStateException("current thread does not hold lock: " + key);
        }
        String lockKey = LOCK_KEY_PREFIX + key;
        String token = threadToken.get();
        long leaseMs = properties.getLock().getDefaultLeaseMs();
        Long result = stringRedisTemplate.execute(
                releaseScript, List.of(lockKey), token, String.valueOf(leaseMs));
        if (result == null || result != 1L) {
            // token 已失效（锁被抢占或过期）：本地状态同样清理，避免泄漏
            held.remove(key);
            stopRenewal(key);
            throw new IllegalStateException("lock ownership lost, release rejected: " + key);
        }
        if (depth == 1) {
            held.remove(key);
            stopRenewal(key);
        } else {
            held.put(key, depth - 1);
        }
    }

    /** 当前线程是否持有该锁 */
    public boolean isHeldByCurrentThread(String key) {
        return heldLocks.get().getOrDefault(key, 0) > 0;
    }

    /**
     * 持锁执行：获取失败快速失败（不阻塞、不排队）。
     *
     * @throws LockAcquireException 未获取到锁
     */
    public <T> T executeWithLock(String key, LockedAction<T> action) {
        return executeWithLock(key, properties.getLock().getDefaultLeaseMs(), action);
    }

    public <T> T executeWithLock(String key, long leaseMs, LockedAction<T> action) {
        if (!tryLock(key, leaseMs)) {
            throw new LockAcquireException("lock busy: " + key);
        }
        try {
            return action.run();
        } finally {
            unlock(key);
        }
    }

    @FunctionalInterface
    public interface LockedAction<T> {
        T run();
    }

    /** 锁获取失败异常 */
    public static class LockAcquireException extends RuntimeException {
        public LockAcquireException(String message) {
            super(message);
        }
    }

    private void startRenewalIfEnabled(String lockKey, String token, long leaseMs) {
        if (!properties.getLock().isWatchdogEnabled()) {
            return;
        }
        long intervalMs = Math.max(100L, leaseMs / properties.getLock().getRenewalFactor());
        ScheduledFuture<?> task = watchdog.scheduleAtFixedRate(() -> {
            Long renewed = stringRedisTemplate.execute(
                    renewScript, List.of(lockKey), token, String.valueOf(leaseMs));
            if (renewed == null || renewed != 1L) {
                // 锁已被抢占或删除：停止续期
                synchronized (renewTasks) {
                    ScheduledFuture<?> self = renewTasks.get(lockKey);
                    if (self != null) {
                        self.cancel(false);
                        renewTasks.remove(lockKey);
                    }
                }
                log.warn("lock renewal stopped (ownership lost), lockKey={}", lockKey);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        synchronized (renewTasks) {
            renewTasks.put(lockKey, task);
        }
    }

    private void stopRenewal(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        synchronized (renewTasks) {
            ScheduledFuture<?> task = renewTasks.remove(lockKey);
            if (task != null) {
                task.cancel(false);
            }
        }
    }
}
