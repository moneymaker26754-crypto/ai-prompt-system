package com.jojo.prompt.bench;

import com.jojo.prompt.minimq.core.MiniBroker;
import com.jojo.prompt.minimq.net.MiniMqClient;
import com.jojo.prompt.minimq.net.MiniMqServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * mini-mq 端到端发布吞吐（单客户端同步请求-响应）。
 *
 * <p>每个 op = 完整链路：客户端编码 → Netty 往返 → LogStore append（长度前缀
 * + <b>每消息 fsync</b>）→ 响应解码。fsync-per-message 是本实现的持久性优先
 * 设计（代码注释已标注批量组提交为后续优化项），因此本基准反映的是
 * 「持久性优先」吞吐，与 RabbitMQ 对比时须注明刷盘策略差异。</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class MiniMqPublishBenchmark {

    private static final byte[] PAYLOAD = "benchmark-payload-128b-".repeat(4).getBytes(StandardCharsets.UTF_8);

    private MiniMqServer server;
    private MiniMqClient client;
    private Path dataDir;

    @Setup
    public void setup() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        dataDir = Files.createTempDirectory("mini-mq-bench");
        server = new MiniMqServer(new MiniBroker(dataDir), port);
        server.start();
        client = new MiniMqClient();
        client.connect("localhost", port);
    }

    @TearDown
    public void teardown() throws Exception {
        client.close();
        server.stop();
    }

    @Benchmark
    public long publish() {
        return client.publish("bench", PAYLOAD);
    }
}
