# mini-MQ vs RabbitMQ 同机对比（2026-09-24）

> 数据来源：`benchmarks/` 模块 JMH 1.37，原始结果 `raw/jmh-mq-2026-09-24.json`。
> 环境：Windows + OpenJDK 25；RabbitMQ 4.x（Docker `rabbitmq:4-management`，localhost:5672，durable queue `bench:rabbit`）；mini-mq 为仓库内自研实现（内嵌 Netty 服务，随机端口，loopback）。
> 方法：`Mode.Throughput`，fork=1，warmup 3×2s，measurement 5×3s，单客户端，128B payload，发布路径。
> 复现：`.\mvnw.cmd -q -pl benchmarks -am package -DskipTests` 后
> `java -cp "benchmarks/target/benchmarks-0.0.1-SNAPSHOT.jar;benchmarks/target/lib/*" org.openjdk.jmh.Main -f 1 -rf json -rff mq.json "com.jojo.prompt.bench.MiniMqPublishBenchmark" "com.jojo.prompt.bench.RabbitMqPublishBenchmark"`

## 结果

| 实现 | 语义 | 吞吐 (ops/s) | ± 误差 |
|---|---|---|---|
| mini-mq | 同步请求-响应，**每消息 fsync** | 1340.5 | 160.7 |
| RabbitMQ fire-and-forget | basicPublish 即返回，无确认 | 18188.3 | 4162.0 |
| RabbitMQ publisher confirms | 每消息 waitForConfirms | 734.0 | 479.5 |

## 解读（诚实边界，简历引用口径）

1. **「确认」路径上 mini-mq 更快**：mini-mq 1340 vs RabbitMQ confirms 734（1.83×）。
   mini-mq 这条路径每消息 fsync（持久性更强），而 RabbitMQ publisher confirm
   只表示 broker 接收、不代表逐消息刷盘——即「更强的持久性下反而更快」，
   主要来自协议精简（单帧请求-响应、无 AMQP 0-9-1 协商开销）与 loopback 部署。
2. **fire-and-forget 差距 13.6× 是刷盘策略差异，不是实现劣势**：RabbitMQ 该模式
   无确认且批量刷盘；mini-mq 每消息 fsync。mini-mq 代码注释已标注「批量组提交」
   为后续优化项，实现后预期显著收窄该差距。
3. **本对比只回答吞吐量级，不回答功能完备性**：mini-mq 无分区/副本/共识/集群
   （README 已声明），RabbitMQ 具备完整企业特性——定位是学习型中间件，
   不以「替代 RabbitMQ」为卖点，以「讲清存储/游标/ack/死信/延迟的工程取舍」为卖点。
4. 单客户端 loopback 场景，延迟主导项是网络往返与刷盘；多客户端扩展性未测。
