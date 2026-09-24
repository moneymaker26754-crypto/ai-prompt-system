package com.jojo.prompt.minimq.core;

import com.jojo.prompt.minimq.storage.LogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * mini-mq 核心 Broker（内存消费语义 + LogStore 持久化）。
 *
 * <p>设计决策：</p>
 * <ol>
 *   <li><b>pull 模型 + 组内独立游标</b>：每个 (topic, group) 一条游标，消费进度互不影响；
 *       消息保留直到 ack（at-least-once），消费者崩溃后可重投；</li>
 *   <li><b>ack 才推进游标</b>：poll 只投递不推进，未 ack 的消息下次 poll 会再次投递
 *       （deliveryCount 递增），这是 at-least-once 语义的直接体现；</li>
 *   <li><b>nack 重试 + 死信</b>：nack 递增投递计数，达到 {@link #MAX_DELIVERY} 后
 *       自动转投 {@code <topic>.dlq} 死信 topic 并推进游标，避免毒消息永久阻塞队列；</li>
 *   <li><b>延迟消息</b>：PriorityBlockingQueue 按到期时间排序 + 单线程调度器每 50ms
 *       提升到期消息，实现秒级精度的延迟投递；</li>
 *   <li><b>条件变量唤醒</b>：poll 空队列时 Condition.await 阻塞，publish 时 signalAll，
 *       避免忙轮询。</li>
 * </ol>
 *
 * <p><b>诚实边界</b>：不实现分区、副本、分布式共识、事务消息——这些是分布式 MQ 的
 * 复杂度核心，单机学习型实现如实声明，不伪造。</p>
 */
public class MiniBroker {

    private static final Logger log = LoggerFactory.getLogger(MiniBroker.class);

    /** 最大投递次数：达到后进死信 */
    public static final int MAX_DELIVERY = 3;
    /** 延迟消息调度周期 */
    private static final long DELAY_SCAN_INTERVAL_MS = 50L;

    private final LogStore logStore;

    /** topic → 该 topic 全部 offset（有序） */
    private final Map<String, NavigableSet<Long>> topicOffsets = new ConcurrentHashMap<>();
    /** offset → topic（DLQ 转投与调试用） */
    private final Map<Long, String> offsetTopic = new ConcurrentHashMap<>();
    /** offset → 产生时间 */
    private final Map<Long, Long> bornTimes = new ConcurrentHashMap<>();
    /** offset → 累计投递次数 */
    private final Map<Long, AtomicInteger> deliveryCounts = new ConcurrentHashMap<>();
    /** "group:topic" → 游标（下一个待投递 offset） */
    private final Map<String, AtomicLong> cursors = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition messageArrived = lock.newCondition();

    private final PriorityBlockingQueue<DelayedEntry> delayedQueue = new PriorityBlockingQueue<>();
    private final ScheduledExecutorService delayScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mini-mq-delay-scheduler");
                t.setDaemon(true);
                return t;
            });

    public MiniBroker(Path dataDir) throws IOException {
        this(new LogStore(dataDir));
    }

    public MiniBroker(LogStore logStore) {
        this.logStore = logStore;
        delayScheduler.scheduleWithFixedDelay(this::promoteDueDelayed,
                DELAY_SCAN_INTERVAL_MS, DELAY_SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** 发布消息，返回全局 offset */
    public long publish(String topic, byte[] payload) throws IOException {
        long offset = logStore.append(payload);
        registerMessage(topic, offset);
        lock.lock();
        try {
            messageArrived.signalAll();
        } finally {
            lock.unlock();
        }
        return offset;
    }

    /**
     * 发布延迟消息。
     *
     * @return 预测派发 offset（= 当前 LogStore 下一个 offset；实际值以到点投递为准，
     *         简化约定见 README）
     */
    public long publishDelayed(String topic, byte[] payload, long deliverAfterMs) {
        delayedQueue.offer(new DelayedEntry(topic, payload,
                System.currentTimeMillis() + deliverAfterMs));
        return logStore.nextOffset();
    }

    private void registerMessage(String topic, long offset) {
        topicOffsets.computeIfAbsent(topic, k -> new TreeSet<>()).add(offset);
        offsetTopic.put(offset, topic);
        bornTimes.put(offset, System.currentTimeMillis());
    }

    /** 到点提升延迟消息为可见 */
    private void promoteDueDelayed() {
        long now = System.currentTimeMillis();
        DelayedEntry entry;
        while ((entry = delayedQueue.peek()) != null && entry.dueTimeMs <= now) {
            delayedQueue.poll();
            try {
                publish(entry.topic, entry.payload);
            } catch (IOException ex) {
                log.error("promote delayed message failed, topic={}", entry.topic, ex);
            }
        }
    }

    /**
     * 拉取消息：组内独立游标，阻塞等待至有消息或超时；投递时 deliveryCount+1。
     *
     * @param timeoutMs 无消息时最长等待毫秒，≤0 表示非阻塞
     */
    public List<MiniMessage> poll(String topic, String group, int maxBatch, long timeoutMs)
            throws IOException {
        String cursorKey = group + ":" + topic;
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        lock.lock();
        try {
            while (true) {
                List<MiniMessage> batch = collectReady(topic, group, cursorKey, maxBatch);
                if (!batch.isEmpty()) {
                    return batch;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return List.of();
                }
                try {
                    messageArrived.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return List.of();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private List<MiniMessage> collectReady(String topic, String group, String cursorKey, int maxBatch)
            throws IOException {
        NavigableSet<Long> offsets = topicOffsets.get(topic);
        if (offsets == null || offsets.isEmpty()) {
            return List.of();
        }
        long cursor = cursors.computeIfAbsent(cursorKey,
                k -> new AtomicLong(offsets.first())).get();
        List<MiniMessage> batch = new ArrayList<>();
        for (long off : offsets.tailSet(cursor)) {
            if (batch.size() >= maxBatch) {
                break;
            }
            int deliveryCount = deliveryCounts.computeIfAbsent(off, k -> new AtomicInteger())
                    .incrementAndGet();
            batch.add(new MiniMessage(off, topic, logStore.read(off), deliveryCount,
                    bornTimes.getOrDefault(off, 0L)));
        }
        return batch;
    }

    /** 确认消费：游标推进到 offset+1（只推进不删除，at-least-once） */
    public void ack(String topic, String group, long offset) {
        String cursorKey = group + ":" + topic;
        AtomicLong cursor = cursors.computeIfAbsent(cursorKey, k -> new AtomicLong(offset));
        cursor.accumulateAndGet(offset + 1, Math::max);
    }

    /**
     * 拒绝消费：投递计数+1；达到 {@link #MAX_DELIVERY} 后转投死信 topic
     * {@code <topic>.dlq} 并推进游标，否则等待下次重投。
     */
    public void nack(String topic, String group, long offset) throws IOException {
        int count = deliveryCounts.computeIfAbsent(offset, k -> new AtomicInteger())
                .incrementAndGet();
        if (count >= MAX_DELIVERY) {
            byte[] payload = logStore.read(offset);
            publish(topic + ".dlq", payload);
            log.warn("message moved to dead letter, topic={}, offset={}, deliveries={}",
                    topic, offset, count);
            ack(topic, group, offset);
        }
    }

    /** 优雅关闭：停调度器、关存储 */
    public void close() throws IOException {
        delayScheduler.shutdownNow();
        logStore.close();
    }

    /** 延迟队列元素：按到期时间排序 */
    private record DelayedEntry(String topic, byte[] payload, long dueTimeMs)
            implements Comparable<DelayedEntry> {
        @Override
        public int compareTo(DelayedEntry other) {
            return Long.compare(this.dueTimeMs, other.dueTimeMs);
        }
    }
}
