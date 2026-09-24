package com.jojo.prompt.minimq.net;

import com.jojo.prompt.minimq.core.MiniBroker;
import com.jojo.prompt.minimq.core.MiniMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端测试：真实 Socket 上完成 publish → poll → ack 全链路。
 */
class EndToEndNettyTest {

    @TempDir
    Path dir;

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void publishPollAckOverRealSocket() throws Exception {
        MiniBroker broker = new MiniBroker(dir);
        int port = freePort();
        MiniMqServer server = new MiniMqServer(broker, port);
        server.start();
        MiniMqClient client = new MiniMqClient();
        try {
            client.connect("127.0.0.1", server.port());

            long offset = client.publish("orders", "hello-netty".getBytes(StandardCharsets.UTF_8));
            assertThat(offset).isZero();

            List<MiniMessage> messages = client.poll("orders", "g1", 5_000);
            assertThat(messages).hasSize(1);
            assertThat(new String(messages.get(0).payload(), StandardCharsets.UTF_8))
                    .isEqualTo("hello-netty");
            assertThat(messages.get(0).deliveryCount()).isEqualTo(1);

            client.ack("orders", "g1", messages.get(0).offset());
            assertThat(client.poll("orders", "g1", 1_000)).isEmpty();
        } finally {
            client.close();
            server.stop();
        }
    }
}
