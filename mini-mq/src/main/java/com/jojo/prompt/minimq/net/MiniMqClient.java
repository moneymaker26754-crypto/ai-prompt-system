package com.jojo.prompt.minimq.net;

import com.jojo.prompt.minimq.core.MiniMessage;
import com.jojo.prompt.minimq.protocol.MiniMqCodec;
import com.jojo.prompt.minimq.protocol.MiniMqCodec.MiniMqFrame;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * mini-mq 客户端：同步请求-响应语义。
 *
 * <p>设计决策：</p>
 * <ol>
 *   <li><b>requestId + Promise 表</b>：单连接多请求并发，响应按 id 精确匹配
 *       （CompletableFuture + ConcurrentHashMap），避免「一问一答锁死连接」；</li>
 *   <li><b>同步 API + 超时</b>：业务侧以阻塞调用简化心智，网络/服务端卡死
 *       由超时兜底；</li>
 *   <li><b>POLL 客户端超时 = 请求方 timeoutMs + 5s 裕量</b>：服务端最长等 3s，
 *       裕量覆盖编解码与调度抖动。</li>
 * </ol>
 */
public class MiniMqClient {

    private static final Logger log = LoggerFactory.getLogger(MiniMqClient.class);
    private static final long REQUEST_TIMEOUT_MS = 5_000L;

    private EventLoopGroup group;
    private Channel channel;
    private final AtomicInteger requestIdGen = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<MiniMqFrame>> pending = new ConcurrentHashMap<>();

    public void connect(String host, int port) throws InterruptedException {
        group = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new MiniMqCodec())
                                .addLast(new ResponseHandler());
                    }
                });
        channel = bootstrap.connect(host, port).sync().channel();
        log.info("mini-mq client connected, {}:{}", host, port);
    }

    /** 发布消息，返回服务端分配的全局 offset */
    public long publish(String topic, byte[] payload) {
        MiniMqFrame response = request(MiniMqFrame.PUBLISH, topic, payload, REQUEST_TIMEOUT_MS);
        return MiniMqFrame.bytesToLong(response.payload());
    }

    /** 发布延迟消息，返回预测派发 offset */
    public long publishDelayed(String topic, byte[] payload, long deliverAfterMs) {
        byte[] body = new byte[8 + payload.length];
        System.arraycopy(MiniMqFrame.longToBytes(deliverAfterMs), 0, body, 0, 8);
        System.arraycopy(payload, 0, body, 8, payload.length);
        MiniMqFrame response = request(MiniMqFrame.PUBLISH_DELAYED, topic, body, REQUEST_TIMEOUT_MS);
        return MiniMqFrame.bytesToLong(response.payload());
    }

    /** 拉取消息（阻塞至有消息或超时，服务端最长等 3s） */
    public List<MiniMessage> poll(String topic, String group, long timeoutMs) {
        MiniMqFrame response = request(MiniMqFrame.POLL, MiniMqFrame.topicKey(group, topic), null,
                Math.max(0, timeoutMs) + REQUEST_TIMEOUT_MS);
        return MiniMqFrame.deserializeMessages(response.payload());
    }

    /** 确认消费 */
    public void ack(String topic, String group, long offset) {
        request(MiniMqFrame.ACK, MiniMqFrame.topicKey(group, topic),
                MiniMqFrame.longToBytes(offset), REQUEST_TIMEOUT_MS);
    }

    /** 拒绝消费（触发重试计数/死信） */
    public void nack(String topic, String group, long offset) {
        request(MiniMqFrame.NACK, MiniMqFrame.topicKey(group, topic),
                MiniMqFrame.longToBytes(offset), REQUEST_TIMEOUT_MS);
    }

    private MiniMqFrame request(byte type, String topic, byte[] payload, long timeoutMs) {
        int requestId = requestIdGen.getAndIncrement();
        CompletableFuture<MiniMqFrame> future = new CompletableFuture<>();
        pending.put(requestId, future);
        channel.writeAndFlush(MiniMqFrame.request(type, requestId, topic, payload));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("request interrupted, type=" + type, ie);
        } catch (TimeoutException te) {
            throw new IllegalStateException("request timeout, type=" + type, te);
        } catch (ExecutionException ee) {
            throw new IllegalStateException("request failed, type=" + type, ee.getCause());
        } finally {
            pending.remove(requestId);
        }
    }

    public void close() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    /** 响应处理器：按 requestId 完成对应 Promise */
    class ResponseHandler extends SimpleChannelInboundHandler<MiniMqFrame> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MiniMqFrame frame) {
            CompletableFuture<MiniMqFrame> future = pending.remove(frame.requestId());
            if (future != null) {
                future.complete(frame);
            } else {
                log.warn("response without pending request, requestId={}, type={}",
                        frame.requestId(), frame.type());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("client connection error, remote={}", ctx.channel().remoteAddress(), cause);
            pending.values().forEach(f -> f.completeExceptionally(cause));
            ctx.close();
        }
    }
}
