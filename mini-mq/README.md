# mini-mq

自研学习型消息中间件核心（Java 21 + Netty，零 Spring 依赖）。

## 定位（诚实边界）

这是一个**单机、学习型**的消息中间件，目标是完整走通「存储引擎 → 消费语义 → 网络协议 → 客户端」四层，
用作工程实践与简历技术亮点。**明确不实现**以下能力，且这是刻意取舍：

| 能力 | 为什么不实现 |
|---|---|
| 分区（Partition） | 分区服务于水平扩容与并行消费，单机场景没有扩容诉求，做了只是装饰 |
| 副本/主从同步 | 高可用依赖复制协议与选举，是分布式系统复杂度核心，单机实现无意义 |
| 分布式事务消息 | 需要跨节点协调，超出单机范围 |
| 零拷贝/mmap | 属于性能优化项，留作与 RabbitMQ 的对比基准里的后续优化点 |

## 架构

```
MiniMqClient ──Netty(私有协议)──▶ MiniMqServer ──▶ MiniBroker ──▶ LogStore(磁盘)
                                   (网络层)         (消费语义)      (存储引擎)
```

## 存储设计（LogStore）

- **顺序写**：消息只追加，磁盘顺序写换取吞吐；
- **长度前缀记录**：`4 字节大端长度 + payload`，精确切分记录，也是恢复时判定尾部损坏的依据；
- **分段滚动**：单段超 `segmentBytes`（默认 64MB）滚动新段，段文件按起始 offset 命名 `%020d.log`；
- **内存索引 + 启动重建**：offset → (段, 位置) 索引放内存；open 时全量扫描重建，尾部损坏记录截断并告警；
- **持久性**：默认每条消息 `fsync`（`FileDescriptor.sync()`）。

## 消费语义（MiniBroker）

- **pull 模型 + 组内独立游标**：每个 `(topic, group)` 一条游标，互不影响；
- **at-least-once**：poll 只投递不推进游标，`ack` 才推进到 `offset+1`；未 ack 的消息下次 poll 重投（deliveryCount 递增）；
- **重试与死信**：`nack` 递增投递计数，达到 `MAX_DELIVERY=3` 自动转投 `<topic>.dlq` 并推进游标，避免毒消息阻塞；
- **延迟消息**：`publishDelayed` 入按到期时间排序的优先级队列，单线程调度器每 50ms 提升到期消息；
- **条件变量唤醒**：空队列 poll 走 `Condition.await`，publish 时 `signalAll`，无忙轮询。

## 网络协议（MiniMqCodec）

帧格式（大端）：`magic(4)|version(1)|type(1)|requestId(4)|topicLen(2)|topic|payloadLen(4)|payload`

| type | 值 | 说明 |
|---|---|---|
| PUBLISH | 1 | 发布，响应 payload = 8 字节 offset |
| POLL | 2 | 拉取，topic 字段承载 `group\u0000topic`，响应 payload = 序列化消息列表 |
| ACK / NACK | 3 / 4 | 确认/拒绝，payload = 8 字节 offset |
| PING / PONG | 5 / 6 | 心跳 |
| PUBLISH_DELAYED | 7 | 延迟发布，payload 前缀 8 字节 = deliverAfterMs（对规格的最小扩展） |

- **半包/粘包**：解码按长度精确切帧，不足时回退读指针等待；
- **同步请求-响应**：客户端 `requestId + CompletableFuture` 表，单连接多请求并发匹配；
- **死连接回收**：服务端 `IdleStateHandler(120s)` 读空闲断连。

## 与 RabbitMQ 的取舍对比

| 维度 | mini-mq | RabbitMQ |
|---|---|---|
| 投递语义 | 至少一次（游标 + ack） | 至少一次/至多一次（ack + prefetch + requeue） |
| 死信 | 投递计数超限自动转 `.dlq` topic | TTL + DLX 交换机，配置灵活 |
| 延迟消息 | 内存优先级队列（重启丢失） | 延迟插件/死信转发，可持久 |
| 持久化 | 每条 fsync（强一致，慢） | 批量 fsync/惰性队列，吞吐与持久性可权衡 |
| 路由 | 直投 topic | Exchange/Queue/Binding 灵活路由 |
| 集群 | 无 | 镜像队列/仲裁队列 |

结论：mini-mq 胜在「自己写、能讲透」，RabbitMQ 胜在生产能力；本仓库主链路仍用 RabbitMQ，
mini-mq 用于非关键链路（行为日志/通知）渐进替换。

## 运行测试

```powershell
# 工作区根目录
.\mvnw.cmd -q -pl mini-mq test
```

测试覆盖：LogStore 往返/滚动/恢复/损坏截断；Broker 按序消费/组隔离/ack/nack 重试/死信/延迟/超时；
协议半包粘包；真实 Socket 端到端 publish→poll→ack。
