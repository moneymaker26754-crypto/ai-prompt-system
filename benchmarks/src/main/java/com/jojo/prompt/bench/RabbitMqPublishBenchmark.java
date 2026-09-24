package com.jojo.prompt.bench;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 同机对比基准（发布路径，两种确认语义）：
 *
 * <ul>
 *   <li>fireAndForget：basicPublish 即返回（服务端异步刷盘，无逐消息确认）；</li>
 *   <li>withPublisherConfirms：每消息 waitForConfirms（确认到达 broker，但
 *       仍非逐消息 fsync——durable 队列默认按周期批量刷盘）。</li>
 * </ul>
 *
 * <p>公平性说明（报告必须引用）：mini-mq 每消息 fsync，RabbitMQ 两种模式
 * 均不逐消息 fsync，因此本对比是「不同持久化语义下的吞吐上限参照」，
 * 而非同语义对照；mini-mq 的批量组提交优化已在代码中标注为后续项。</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class RabbitMqPublishBenchmark {

    private static final byte[] PAYLOAD = "benchmark-payload-128b-".repeat(4).getBytes(StandardCharsets.UTF_8);

    @Param({"fireAndForget", "withPublisherConfirms"})
    public String mode;

    private Connection connection;
    private Channel channel;

    @Setup
    public void setup() throws Exception {
        String host = System.getProperty("bench.rabbitmq.host", "localhost");
        int port = Integer.parseInt(System.getProperty("bench.rabbitmq.port", "5672"));
        String user = System.getProperty("bench.rabbitmq.user", "admin");
        String password = System.getProperty("bench.rabbitmq.password", "123456");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(password);
        connection = factory.newConnection("jmh-bench");
        channel = connection.createChannel();
        channel.queueDeclare("bench:rabbit", true, false, false, null);
        if ("withPublisherConfirms".equals(mode)) {
            channel.confirmSelect();
        }
    }

    @TearDown
    public void teardown() throws Exception {
        channel.close();
        connection.close();
    }

    @Benchmark
    public void publish() throws Exception {
        channel.basicPublish("", "bench:rabbit", null, PAYLOAD);
        if ("withPublisherConfirms".equals(mode)) {
            channel.waitForConfirms();
        }
    }
}
