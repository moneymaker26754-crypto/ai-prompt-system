package com.jojo.prompt.minimq.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * mini-mq starter 配置属性。
 *
 * <p>默认关闭（enabled=false）：业务方显式开启并指定 broker 地址，
 * 保证「渐进替换」模式下误引入不会改变现有行为。</p>
 */
@ConfigurationProperties(prefix = "prompt.mini-mq")
public class MiniMqProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 16888;
    private Consumer consumer = new Consumer();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    public static class Consumer {
        /** 单次 poll 超时（毫秒） */
        private long pollTimeoutMs = 2_000L;
        /** 单次 poll 最大消息数 */
        private int maxBatch = 50;
        /** 监听器线程池大小 */
        private int poolSize = 2;

        public long getPollTimeoutMs() {
            return pollTimeoutMs;
        }

        public void setPollTimeoutMs(long pollTimeoutMs) {
            this.pollTimeoutMs = pollTimeoutMs;
        }

        public int getMaxBatch() {
            return maxBatch;
        }

        public void setMaxBatch(int maxBatch) {
            this.maxBatch = maxBatch;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }
}
