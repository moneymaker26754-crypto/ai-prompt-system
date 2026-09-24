package com.jojo.prompt.minimq.protocol;

import com.jojo.prompt.minimq.core.MiniMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * mini-mq 私有协议编解码器。
 *
 * <p>帧格式（大端）：</p>
 * <pre>
 * magic(4) | version(1) | type(1) | requestId(4) | topicLen(2) | topic | payloadLen(4) | payload
 * </pre>
 *
 * <p>设计决策：</p>
 * <ol>
 *   <li><b>魔数 + 版本</b>：连接复用/误连时可快速判定非法报文；版本为协议演进留位；</li>
 *   <li><b>requestId 关联</b>：同步请求-响应模式下客户端按 requestId 匹配 Promise；</li>
 *   <li><b>长度前缀</b>：解码端按长度精确切帧，半包时回退读指针等待、
 *       粘包时一帧一帧消费，是经典 TCP 流处理方案；</li>
 *   <li><b>payload 透明</b>：POLL 响应把多条 MiniMessage 序列化进 payload，
 *      协议层不关心业务结构，扩展性交给上层。</li>
 * </ol>
 *
 * <p>说明：PUBLISH_DELAYED(7) 是在规格 6 类报文基础上的最小扩展——服务端必须区分
 * 「立即投递」与「延迟投递」才能表达 deliverAfterMs，报文 payload 前缀 8 字节大端
 * 存延迟毫秒数。</p>
 */
public class MiniMqCodec extends ByteToMessageCodec<MiniMqCodec.MiniMqFrame> {

    private static final int HEADER_FIXED_BYTES = 4 + 1 + 1 + 4 + 2;

    @Override
    protected void encode(ChannelHandlerContext ctx, MiniMqFrame frame, ByteBuf out) {
        byte[] topic = frame.topic() == null ? new byte[0] : frame.topic().getBytes(StandardCharsets.UTF_8);
        byte[] payload = frame.payload() == null ? new byte[0] : frame.payload();
        out.writeInt(frame.magic());
        out.writeByte(frame.version());
        out.writeByte(frame.type());
        out.writeInt(frame.requestId());
        out.writeShort(topic.length);
        out.writeBytes(topic);
        out.writeInt(payload.length);
        out.writeBytes(payload);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();
        if (in.readableBytes() < HEADER_FIXED_BYTES) {
            return; // 半包：等待更多字节
        }
        int magic = in.readInt();
        byte version = in.readByte();
        byte type = in.readByte();
        int requestId = in.readInt();
        int topicLen = in.readShort();
        if (in.readableBytes() < topicLen + 4) {
            in.resetReaderIndex();
            return;
        }
        byte[] topicBytes = new byte[topicLen];
        in.readBytes(topicBytes);
        int payloadLen = in.readInt();
        if (in.readableBytes() < payloadLen) {
            in.resetReaderIndex();
            return; // 半包：payload 未到齐
        }
        byte[] payload = new byte[payloadLen];
        in.readBytes(payload);
        if (magic != MiniMqFrame.MAGIC) {
            throw new IllegalArgumentException("bad magic: " + Integer.toHexString(magic));
        }
        out.add(new MiniMqFrame(magic, version, type, requestId,
                topicLen == 0 ? null : new String(topicBytes, StandardCharsets.UTF_8),
                payloadLen == 0 ? null : payload));
    }

    /** 协议帧 */
    public record MiniMqFrame(int magic, byte version, byte type, int requestId,
                              String topic, byte[] payload) {

        public static final int MAGIC = 0x4D4D5141; // "MMQA"
        public static final byte VERSION = 1;

        public static final byte PUBLISH = 1;
        public static final byte POLL = 2;
        public static final byte ACK = 3;
        public static final byte NACK = 4;
        public static final byte PING = 5;
        public static final byte PONG = 6;
        /** 延迟发布（payload 前缀 8 字节 = deliverAfterMs） */
        public static final byte PUBLISH_DELAYED = 7;

        /** 构建请求帧（响应帧由服务端按原 requestId 回填） */
        public static MiniMqFrame request(byte type, int requestId, String topic, byte[] payload) {
            return new MiniMqFrame(MAGIC, VERSION, type, requestId, topic, payload);
        }

        // ---- 业务载荷辅助 ----

        /** POLL/ACK/NACK 的 topic 字段承载 "group\u0000topic" 复合键（组名/主题名不含 \u0000） */
        public static String topicKey(String group, String topic) {
            return group + '\u0000' + topic;
        }

        /** 拆复合键 → [group, topic] */
        public static String[] splitTopicKey(String key) {
            if (key == null) {
                throw new IllegalArgumentException("topic key required");
            }
            int sep = key.indexOf('\u0000');
            if (sep < 0) {
                throw new IllegalArgumentException("invalid topic key: " + key);
            }
            return new String[]{key.substring(0, sep), key.substring(sep + 1)};
        }

        public static byte[] longToBytes(long value) {
            byte[] bytes = new byte[8];
            for (int i = 7; i >= 0; i--) {
                bytes[i] = (byte) value;
                value >>>= 8;
            }
            return bytes;
        }

        public static long bytesToLong(byte[] bytes) {
            long value = 0;
            for (byte b : bytes) {
                value = (value << 8) | (b & 0xFF);
            }
            return value;
        }

        /** 序列化消息列表（POLL 响应 payload） */
        public static byte[] serializeMessages(List<MiniMessage> messages) {
            int size = 4;
            for (MiniMessage m : messages) {
                size += 8 + 2 + m.topic().getBytes(StandardCharsets.UTF_8).length
                        + 4 + m.payload().length + 4 + 8;
            }
            byte[] out = new byte[size];
            int pos = writeInt(out, 0, messages.size());
            for (MiniMessage m : messages) {
                pos = writeLong(out, pos, m.offset());
                byte[] topic = m.topic().getBytes(StandardCharsets.UTF_8);
                pos = writeShort(out, pos, topic.length);
                pos = writeBytes(out, pos, topic);
                pos = writeInt(out, pos, m.payload().length);
                pos = writeBytes(out, pos, m.payload());
                pos = writeInt(out, pos, m.deliveryCount());
                pos = writeLong(out, pos, m.bornTimeMs());
            }
            return out;
        }

        /** 反序列化消息列表 */
        public static List<MiniMessage> deserializeMessages(byte[] bytes) {
            if (bytes == null || bytes.length < 4) {
                return List.of();
            }
            int pos = 0;
            int count = readInt(bytes, pos);
            pos += 4;
            List<MiniMessage> messages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long offset = readLong(bytes, pos);
                pos += 8;
                int topicLen = readShort(bytes, pos);
                pos += 2;
                String topic = new String(bytes, pos, topicLen, StandardCharsets.UTF_8);
                pos += topicLen;
                int payloadLen = readInt(bytes, pos);
                pos += 4;
                byte[] payload = new byte[payloadLen];
                System.arraycopy(bytes, pos, payload, 0, payloadLen);
                pos += payloadLen;
                int deliveryCount = readInt(bytes, pos);
                pos += 4;
                long bornTimeMs = readLong(bytes, pos);
                pos += 8;
                messages.add(new MiniMessage(offset, topic, payload, deliveryCount, bornTimeMs));
            }
            return messages;
        }

        private static int writeLong(byte[] buf, int pos, long v) {
            for (int i = 7; i >= 0; i--) {
                buf[pos + i] = (byte) v;
                v >>>= 8;
            }
            return pos + 8;
        }

        private static int writeInt(byte[] buf, int pos, int v) {
            buf[pos] = (byte) (v >>> 24);
            buf[pos + 1] = (byte) (v >>> 16);
            buf[pos + 2] = (byte) (v >>> 8);
            buf[pos + 3] = (byte) v;
            return pos + 4;
        }

        private static int writeShort(byte[] buf, int pos, int v) {
            buf[pos] = (byte) (v >>> 8);
            buf[pos + 1] = (byte) v;
            return pos + 2;
        }

        private static int writeBytes(byte[] buf, int pos, byte[] src) {
            System.arraycopy(src, 0, buf, pos, src.length);
            return pos + src.length;
        }

        private static long readLong(byte[] buf, int pos) {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (buf[pos + i] & 0xFF);
            }
            return v;
        }

        private static int readInt(byte[] buf, int pos) {
            return ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16)
                    | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
        }

        private static int readShort(byte[] buf, int pos) {
            return ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        }
    }
}
