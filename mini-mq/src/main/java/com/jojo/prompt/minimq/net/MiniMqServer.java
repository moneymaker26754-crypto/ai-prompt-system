package com.jojo.prompt.minimq.net;

import com.jojo.prompt.minimq.core.MiniBroker;
import com.jojo.prompt.minimq.protocol.MiniMqCodec;
import com.jojo.prompt.minimq.protocol.MiniMqCodec.MiniMqFrame;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * mini-mq 内嵌服务端：Netty 网络层 + MiniBroker 业务核心。
 *
 * <p>设计决策：</p>
 * <ol>
 *   <li><b>boss=1 / worker=4</b>：连接接入与 IO 处理分离，学习型项目足够；</li>
 *   <li><b>请求-响应同帧协议</b>：响应复用请求 requestId 与 type，客户端按 id 匹配；</li>
 *   <li><b>IdleStateHandler(120s)</b>：读空闲即断连，及时回收死连接；</li>
 *   <li><b>POLL 服务端超时 3s</b>：避免单连接长挂占满 worker 线程。</li>
 * </ol>
 */
public class MiniMqServer {

    private static final Logger log = LoggerFactory.getLogger(MiniMqServer.class);
    private static final int MAX_POLL_BATCH = 50;
    private static final long POLL_TIMEOUT_MS = 3_000L;

    private final MiniBroker broker;
    private final int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public MiniMqServer(MiniBroker broker, int port) {
        this.broker = broker;
        this.port = port;
    }

    /** 构造时传入的绑定端口 */
    public int port() {
        return port;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(4);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(120, 0, 0, TimeUnit.SECONDS))
                                .addLast(new MiniMqCodec())
                                .addLast(new BrokerHandler(broker));
                    }
                });
        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("mini-mq server started, port={}", port);
    }

    public void stop() throws InterruptedException, IOException {
        if (serverChannel != null) {
            serverChannel.close().sync();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().sync();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().sync();
        }
        broker.close();
        log.info("mini-mq server stopped, port={}", port);
    }

    /** 请求处理器：按帧类型分发到 Broker */
    static class BrokerHandler extends SimpleChannelInboundHandler<MiniMqFrame> {

        private final MiniBroker broker;

        BrokerHandler(MiniBroker broker) {
            this.broker = broker;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MiniMqFrame frame) throws Exception {
            byte type = frame.type();
            if (type == MiniMqFrame.PUBLISH) {
                long offset = broker.publish(frame.topic(), frame.payload());
                ctx.writeAndFlush(MiniMqFrame.request(MiniMqFrame.PUBLISH, frame.requestId(), null,
                        MiniMqFrame.longToBytes(offset)));
                return;
            }
            if (type == MiniMqFrame.PUBLISH_DELAYED) {
                byte[] payload = frame.payload();
                long deliverAfterMs = MiniMqFrame.bytesToLong(
                        java.util.Arrays.copyOfRange(payload, 0, 8));
                byte[] body = java.util.Arrays.copyOfRange(payload, 8, payload.length);
                long predicted = broker.publishDelayed(frame.topic(), body, deliverAfterMs);
                ctx.writeAndFlush(MiniMqFrame.request(MiniMqFrame.PUBLISH_DELAYED, frame.requestId(), null,
                        MiniMqFrame.longToBytes(predicted)));
                return;
            }
            if (type == MiniMqFrame.POLL) {
                String[] parts = MiniMqFrame.splitTopicKey(frame.topic());
                List<com.jojo.prompt.minimq.core.MiniMessage> messages =
                        broker.poll(parts[1], parts[0], MAX_POLL_BATCH, POLL_TIMEOUT_MS);
                ctx.writeAndFlush(MiniMqFrame.request(MiniMqFrame.POLL, frame.requestId(), null,
                        MiniMqFrame.serializeMessages(messages)));
                return;
            }
            if (type == MiniMqFrame.ACK || type == MiniMqFrame.NACK) {
                String[] parts = MiniMqFrame.splitTopicKey(frame.topic());
                long offset = MiniMqFrame.bytesToLong(frame.payload());
                if (type == MiniMqFrame.ACK) {
                    broker.ack(parts[1], parts[0], offset);
                } else {
                    broker.nack(parts[1], parts[0], offset);
                }
                ctx.writeAndFlush(MiniMqFrame.request(type, frame.requestId(), null, frame.payload()));
                return;
            }
            if (type == MiniMqFrame.PING) {
                ctx.writeAndFlush(MiniMqFrame.request(MiniMqFrame.PONG, frame.requestId(), null, null));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("connection closed on error, remote={}", ctx.channel().remoteAddress(), cause);
            ctx.close();
        }
    }
}
