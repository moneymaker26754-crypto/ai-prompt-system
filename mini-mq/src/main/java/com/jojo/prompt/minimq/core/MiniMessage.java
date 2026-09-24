package com.jojo.prompt.minimq.core;

/**
 * 投递给消费者的消息视图。
 *
 * @param offset         全局唯一 offset（消费语义的坐标：ack/nack 都围绕它）
 * @param topic          所属 topic
 * @param payload        消息体
 * @param deliveryCount  本次投递时的累计投递次数（nack 重投会递增，达上限进死信）
 * @param bornTimeMs     消息产生时间戳
 */
public record MiniMessage(long offset, String topic, byte[] payload, int deliveryCount, long bornTimeMs) {
}
