package com.jojo.prompt.minimq.spring;

import com.jojo.prompt.minimq.core.MiniMessage;
import com.jojo.prompt.minimq.net.MiniMqClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mini-mq 的 Spring 门面：生命周期管理 + 字符串友好 API。
 *
 * <p>设计决策：</p>
 * <ol>
 *   <li><b>InitializingBean/DisposableBean</b>：随容器启停连接/释放，
 *       业务代码无需感知底层 EventLoopGroup；</li>
 *   <li><b>发送失败 fail-fast</b>：行为日志/通知属非关键链路，调用方
 *       自行 try-catch 降级（与 RabbitMQ 主链路策略一致）；</li>
 *   <li><b>isConnected 健康探针</b>：供 actuator health 与监听容器自检。</li>
 * </ol>
 */
@Slf4j
public class MiniMqTemplate implements InitializingBean, DisposableBean {

    private final MiniMqProperties properties;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile MiniMqClient client;

    public MiniMqTemplate(MiniMqProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!properties.isEnabled()) {
            return;
        }
        client = new MiniMqClient();
        client.connect(properties.getHost(), properties.getPort());
        connected.set(true);
        log.info("mini-mq template connected, {}:{}", properties.getHost(), properties.getPort());
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
        connected.set(false);
    }

    /** 发布字符串消息，返回 offset */
    public long publish(String topic, String payload) {
        return publish(topic, payload.getBytes(StandardCharsets.UTF_8));
    }

    public long publish(String topic, byte[] payload) {
        assertConnected();
        return client.publish(topic, payload);
    }

    /** 发布延迟消息（毫秒），返回预测派发 offset */
    public long publishDelayed(String topic, String payload, long deliverAfterMs) {
        return publishDelayed(topic, payload.getBytes(StandardCharsets.UTF_8), deliverAfterMs);
    }

    public long publishDelayed(String topic, byte[] payload, long deliverAfterMs) {
        assertConnected();
        return client.publishDelayed(topic, payload, deliverAfterMs);
    }

    public List<MiniMessage> poll(String topic, String group, long timeoutMs) {
        assertConnected();
        return client.poll(topic, group, timeoutMs);
    }

    public void ack(String topic, String group, long offset) {
        assertConnected();
        client.ack(topic, group, offset);
    }

    public void nack(String topic, String group, long offset) {
        assertConnected();
        client.nack(topic, group, offset);
    }

    public boolean isConnected() {
        return connected.get();
    }

    private void assertConnected() {
        if (!connected.get() || client == null) {
            throw new IllegalStateException("mini-mq template not connected (prompt.mini-mq.enabled=false?)");
        }
    }
}
