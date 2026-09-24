package com.jojo.prompt.minimq.spring;

import com.jojo.prompt.minimq.core.MiniBroker;
import com.jojo.prompt.minimq.net.MiniMqServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * starter 端到端测试：真实 MiniMqServer + @MiniMqListener 消费全链路。
 */
@SpringBootTest(classes = MiniMqStarterTest.Config.class)
class MiniMqStarterTest {

    static MiniMqServer server;
    static MiniBroker broker;
    static int port;

    @BeforeAll
    static void startServer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path dataDir = Files.createTempDirectory("mini-mq-starter-test");
        broker = new MiniBroker(dataDir);
        server = new MiniMqServer(broker, port);
        server.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (broker != null) {
            broker.close();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("prompt.mini-mq.enabled", () -> true);
        registry.add("prompt.mini-mq.host", () -> "localhost");
        registry.add("prompt.mini-mq.port", () -> port);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Config {

        static final BlockingQueue<String> received = new LinkedBlockingQueue<>();

        @Bean
        ConsumerBean consumerBean() {
            return new ConsumerBean();
        }

        static class ConsumerBean {
            @MiniMqListener(topic = "test-topic", group = "test-group")
            public void onMessage(String payload) {
                received.add(payload);
            }
        }
    }

    @Autowired
    private MiniMqTemplate template;

    @Test
    void publishIsConsumedByAnnotatedListener() throws Exception {
        template.publish("test-topic", "hello-mini-mq");
        String received = Config.received.poll(10, TimeUnit.SECONDS);
        assertEquals("hello-mini-mq", received, "@MiniMqListener 应消费到发布的消息");
        assertEquals(true, template.isConnected());
    }
}
