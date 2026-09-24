package com.jojo.prompt.minimq.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MiniBroker 消费语义测试：按序消费、组隔离、ack、nack 重试、死信、延迟、超时。
 */
class MiniBrokerTest {

    @TempDir
    Path dir;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(MiniMessage m) {
        return new String(m.payload(), StandardCharsets.UTF_8);
    }

    @Test
    void publishThenConsumeInOrderWithinGroup() throws Exception {
        MiniBroker broker = new MiniBroker(dir);
        long o1 = broker.publish("t", bytes("m1"));
        long o2 = broker.publish("t", bytes("m2"));
        long o3 = broker.publish("t", bytes("m3"));

        List<MiniMessage> batch = broker.poll("t", "g1", 50, 1_000);
        assertThat(batch).extracting(MiniBrokerTest::text).containsExactly("m1", "m2", "m3");
        assertThat(batch).extracting(MiniMessage::offset).containsExactly(o1, o2, o3);
        assertThat(batch).allMatch(m -> m.deliveryCount() == 1);
        broker.close();
    }

    @Test
    void cursorsAreIndependentPerGroup() throws Exception {
        MiniBroker broker = new MiniBroker(dir);
        long o1 = broker.publish("t", bytes("m1"));

        List<MiniMessage> a = broker.poll("t", "gA", 50, 1_000);
        List<MiniMessage> b = broker.poll("t", "gB", 50, 1_000);
        assertThat(a).hasSize(1);
        assertThat(b).hasSize(1);
        assertThat(a.get(0).offset()).isEqualTo(o1);
        assertThat(b.get(0).offset()).isEqualTo(o1);
        broker.close();
    }

    @Test
    void ackAdvancesCursorSoNoRedelivery() throws Exception {
        MiniBroker broker = new MiniBroker(dir);
        long o1 = broker.publish("t", bytes("m1"));

        MiniMessage m = broker.poll("t", "g1", 50, 1_000).get(0);
        broker.ack("t", "g1", m.offset());
        assertThat(broker.poll("t", "g1", 50, 300)).isEmpty();
        assertThat(o1).isEqualTo(m.offset());
        broker.close();
    }

    @Test
    void nackRedeliversWithIncrementedDeliveryCount() throws Exception {
        MiniBroker broker = new MiniBroker(dir);
        broker.publish("t", bytes("m1"));

        MiniMessage first = broker.poll("t", "g1", 50, 1_000).get(0);
        assertThat(first.deliveryCount()).isEqualTo(1);
        broker.nack("t", "g1", first.offset()); // nack 计数 +1 → 2

        MiniMessage second = broker.poll("t", "g1", 50, 1_000).get(0);
        // 重投时 poll 再次 +1 → 3（规格：poll 投递 +1、nack +1）
        assertThat(second.deliveryCount()).isEqualTo(3);
        broker.close();
    }

    @Test
    void exceedingMaxDeliveryMovesToDlqTopic() throws Exception {
        MiniBroker broker = new MiniBroker(dir);
        broker.publish("orders", bytes("poison"));

        MiniMessage m1 = broker.poll("orders", "g1", 50, 1_000).get(0); // dc=1
        broker.nack("orders", "g1", m1.offset());                        // dc=2
        MiniMessage m2 = broker.poll("orders", "g1", 50, 1_000).get(0);  // dc=3
        assertThat(m2.deliveryCount()).isEqualTo(3);
        broker.nack("orders", "g1", m2.offset());                        // dc=4 >= 3 → 死信

        // 原 topic 不再投递；死信 topic 可消费到同一 payload
        assertThat(broker.poll("orders", "g1", 50, 300)).isEmpty();
        List<MiniMessage> dlq = broker.poll("orders.dlq", "g1", 50, 1_000);
        assertThat(dlq).hasSize(1);
        assertThat(text(dlq.get(0))).isEqualTo("poison");
        assertThat(dlq.get(0).topic()).isEqualTo("orders.dlq");
        broker.close();
    }

    @Test
    void delayedMessageBecomesVisibleAroundDueTime() throws Exception {
        MiniBroker broker = new MiniBroker(dir);
        long start = System.currentTimeMillis();
        broker.publishDelayed("t", bytes("later"), 500L);

        // 立即 poll 不应立刻拿到（给调度器一个扫描周期也远早于 500ms）
        List<MiniMessage> early = broker.poll("t", "g1", 50, 100);
        assertThat(early).isEmpty();

        List<MiniMessage> due = broker.poll("t", "g1", 50, 2_000);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(due).hasSize(1);
        assertThat(text(due.get(0))).isEqualTo("later");
        assertThat(elapsed).isBetween(200L, 800L); // 期望 500ms，±300ms 容差
        broker.close();
    }

    @Test
    void pollTimesOutWithEmptyListWhenNoMessage() throws Exception {
        MiniBroker broker = new MiniBroker(dir);
        long start = System.currentTimeMillis();
        List<MiniMessage> result = broker.poll("empty", "g1", 50, 200);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(result).isEmpty();
        assertThat(elapsed).isBetween(150L, 1_000L);
        broker.close();
    }
}
