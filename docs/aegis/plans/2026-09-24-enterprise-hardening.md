# 企业级改造与纯技术组件实施计划（2026-09-24）

> 本计划由 writing-plans 技能产出。执行依据：用户已确认范围 = 全套（主项目加固 + 自研 mini-MQ + 自研 Starter + 压测报告 + README + 提交 GitHub），mini-MQ 采用同仓库 Maven 模块并接入主项目非关键链路。

## 1. Goal

在不破坏现有 REST API、DB schema、Redis key 结构与 RabbitMQ 主链路的前提下：

1. 主项目获得真实动机驱动的分布式能力（幂等、分布式锁、滑动窗口限流、缓存穿透/击穿/雪崩三防），并产出可复现压测数据；
2. 同仓库新增自研 mini-MQ（存储引擎 + 消费语义 + 网络层 + 延迟消息），附 JMH 基准与 RabbitMQ 同机对比；
3. 新增两个 Spring Boot Starter（infra 组件 starter + mini-mq starter），主项目 dogfooding；
4. 扩充 RAG 检索与计数基准数据（Recall@K / 混合检索 / HNSW、并发打满找拐点）；
5. 测试全绿后重写 README（架构图 + STAR 亮点清单 + 数据），提交推送到 origin/master。

**非目标（明确不做）**：微服务拆分、注册中心/网关/配置中心、K8s 部署编排、DB 迁移、前端。

## 2. Architecture

- **主项目**：保持模块化单体。Java 21 / Spring Boot 3.5.x，包结构不变。
- **新增 Maven 模块（同仓库多模块改造）**：
  - `infra-starter`（artifactId `prompt-infra-spring-boot-starter`）：分布式锁 / 滑动窗口限流注解 / 幂等注解 / 布隆过滤器，基于 Redis + Lua，Micrometer 指标；
  - `mini-mq`（artifactId `mini-mq`）：纯 Java 消息中间件核心（存储 + 语义 + Netty 网络 + 客户端 API），无 Spring 依赖；
  - `mini-mq-spring-boot-starter`：mini-mq 客户端的自动装配（Template + @MiniMqListener + 健康检查 + 指标）；
  - `app`（现主项目）：依赖以上三个模块。
- 根 pom 变为 aggregator + parent，`app` 目录结构调整为子模块（保持源码 package 路径不变，仅物理位置迁移到 `app/`，与 ai-service 平级）。

## 3. Tech Stack

Java 21、Spring Boot 3.5.13、MyBatis-Plus、Redis(Lettuce+Lua)、RabbitMQ(保留)、Netty 4.1(新增)、JMH(新增)、Micrometer(已有)、Python FastAPI ai-service(不动)。

## 4. Baseline / Authority Refs

- 基线 HEAD：`6144f0a`（2026-09-24，含 3 个基线 commit：d27e04e / 776b57a / 6144f0a）。
- 基线测试状态（本计划开工前实测）：
  - Java：`mvnw test` 全绿（15 个测试类，`MVN_EXIT=0`；已在 pom 加 surefire `-Djdk.net.URLClassPath.disableClassPathURLCheck=true` 修复 JDK 25 fork 崩溃）；
  - Python：`pytest` 122 passed（修复 import 拼写与 OR 语义断言后）。
- 关键基线文件（已读）：`RedisCacheServiceImpl`（487 行，计数/排行/锁/限流/去重）、`PromptMqProducer`（延迟队列投递 + dispatchKey 防重）、`RabbitMqConfig`、`BehaviorLogListener`/`NotificationListener`（@Async 本地监听，mini-MQ 接入目标）、`PromptOptimizationServiceImpl#confirmAsPrompt`（幂等缺口）、`PromptCommandServiceImpl`（乐观锁版本控制）。

## 5. Compatibility Boundary

- REST API 路径/参数/响应零变更。
- DB schema 零变更（无迁移文件）。
- 现有 Redis key 前缀零变更；新增 key：`bloom:prompt:*`、`rate:limit:sw:*`、`lock:*`、`idem:*`。
- RabbitMQ 审核链 + 计数延迟同步链路保留为默认；mini-MQ 仅接管「行为日志 / 通知」两条非关键链路，由 `prompt.mq.mode`（默认 `rabbitmq`）开关控制，可一键回退。
- ai-service 对外契约不变；P6 只新增数据与脚本。

## 6. TDD Route

- **Mode: off → decision: skipped**（无显式 TDD 授权；采用最小改动 + 事后回归验证）。每个任务的验证 = 模块单测 + `mvnw test` 全量回归 + 对应压测/手工 smoke；不回滚到 RED/GREEN 仪式。

## 7. Change Necessity

用户明确要求企业级改造与纯技术组件（已确认范围），属代码级需求，docs/config-only 无法满足。每个任务的最小代码边界在任务内标注。

## 8. Ripple Signal Triage

- `RedisCacheServiceImpl` 是共享核心（计数/锁/限流/去重聚合点）：限流实现替换为滑动窗口 Lua 时，签名不变、调用点不变，仅内部实现变更；锁替换仅动 `syncCountToDb` 一处。
- `prompt.count.mode` A/B 开关（已存在于基线）保留并文档化，用于 P6 计数对比。
- 新模块不触碰 `ai-service`；`PromptMqProducer` 不改（RabbitMQ 主链路）。
- 无 DB schema / 公共契约 / 迁移面。

## 9. Tasks（执行单元，非 git 历史单元）

### P0 基线（✅ 已完成）
- 修复 langchian 拼写、OR 语义测试同步、surefire JDK25 崩溃；三 commit：d27e04e / 776b57a / 6144f0a；双端测试全绿。

### P1 infra-starter 模块（新 owner：`infra-starter/`）
- **T1.1** 模块骨架：pom（spring-boot-autoconfigure + configuration-processor）、`PromptInfraProperties`、`spring.factories`/`AutoConfiguration.imports`、`@EnablePromptInfra`。
  - 验证：`mvnw -pl infra-starter install` 通过；autoconfigure 报告可见。
- **T1.2** RedisLock：SET NX PX + Lua 释放校验 token + 可重入（HINCRBY）+ WatchDog 续期。文件：`infra-starter/src/main/java/com/jojo/infra/lock/*`。
  - 验证：`RedisLockTest`（互斥/可重入/超时释放/watchdog 续期/并发 20 线程争抢仅 1 成功）。
- **T1.3** 滑动窗口限流：`@RateLimit`（窗口秒/上限/维度 ip|user|自定义 SpEL）+ Lua ZSET 原子判定 + AOP 拦截器。
  - 验证：`RateLimitTest`（窗口边界/并发 50 线程计数准确/维度隔离）+ Lua 脚本正确性。
- **T1.4** 幂等：`@Idempotent`（key 维度 + Redis SETNX token + 业务成功才置成功位）。
  - 验证：`IdempotentTest`（重复请求单次生效/并发双请求仅 1 执行/失败可重试）。
- **T1.5** 布隆过滤器：Redis bitmap（多 hash，容量+误判率参数，add/mightContain/重建）。
  - 验证：`BloomFilterTest`（1k 元素实测误判率 ≤ 理论值 ×1.5；不存在元素判定）。
- **T1.6** Micrometer 指标：锁等待时间、限流拒绝计数、幂等命中计数、布隆命中。
  - 验证：测试断言 meter 注册。

### P2 主项目接入 infra-starter
- **T2.1** 根 pom 多模块化：`app/` 子模块迁移（源码物理移动，package 不变），根 pom aggregator。
  - 验证：`mvnw test` 全绿 + git 移动检测（rename 识别）。
- **T2.2** confirm 幂等+锁：`PromptOptimizationServiceImpl#confirmAsPrompt` 加 `@Idempotent(key = userId+recordId)` + RedisLock 包裹。
  - 验证：新增单测（同 record 并发 confirm 仅创建 1 条 Prompt）。
- **T2.3** 限流升级：`RedisCacheServiceImpl#trySearchAllowed/tryLoginAllowed` 内部切换为滑动窗口 Lua（签名不变）；`@RateLimit` 应用于 copy 接口（真实动机：高频写 + 已有去重窗口仅 60s）。
  - 验证：新增单测 + 全量回归。
- **T2.4** 布隆防穿透：`PromptQueryServiceImpl#getPromptDetail` 查缓存前查布隆；prompt 创建时加入布隆。
  - 验证：新增单测（不存在 id 不落 null 缓存仍能拦截——布隆+null 双层）。
- **T2.5** 击穿互斥重建：详情缓存 miss 后 RedisLock + double-check 重建。
  - 验证：新增单测（并发 miss 仅 1 次 DB 查询）。
- **T2.6** 锁替换：`syncCountToDb` 用 RedisLock 替换原 setIfAbsent。
  - 验证：既有 `PromptCountSyncConsumerTest` 全绿。

### P3 mini-mq 模块（新 owner：`mini-mq/`）
- **T3.1** LogStore 存储引擎：分段 append-only 文件、4KB 对齐、内存 offset 索引、启动恢复。
  - 验证：`LogStoreTest`（append/read/恢复/滚动分段）。
- **T3.2** 消费语义：Topic/Queue/ConsumerGroup/offset、ack、重试次数→DLQ、延迟消息（时间轮）。
  - 验证：`BrokerSemanticsTest`（消费组隔离/at-least-once/重试后进 DLQ/延迟精确到秒）。
- **T3.3** Netty 网络层 + 私有协议（魔数/版本/type/length/body）+ 心跳。
  - 验证：`ProtocolCodecTest` + 端到端 socket 测试。
- **T3.4** 客户端 API（Producer.send / Consumer.pull batch）。
  - 验证：集成测试（生产→消费→ack）。
- **T3.5** JMH 基准：存储引擎写读吞吐、端到端吞吐/延迟分位。
  - 验证：JMH 输出可复现（记录 JDK/机器参数）。

### P4 mini-mq-spring-boot-starter（新 owner：`mini-mq-spring-boot-starter/`）
- **T4.1** 自动装配：`MiniMqProperties`、`MiniMqTemplate`、`@MiniMqListener` 扫描注册、健康检查、指标。
  - 验证：starter 单测 + `MiniMqAutoConfigurationTest`。
- **T4.2** 依赖隔离（starter 依赖 mini-mq 客户端，不传 Spring 到核心）。
  - 验证：`mvnw -pl mini-mq-spring-boot-starter install`。

### P5 主项目接入 mini-MQ（非关键链路）
- **T5.1** `prompt.mq.mode` 开关（默认 rabbitmq）：`BehaviorLogListener`/`NotificationListener` 在 minimq 模式下通过 MiniMqTemplate 投递；`@MiniMqListener` 消费者写行为日志/发送通知。
  - 验证：单测（mock 模式切换）+ 本地起 broker 手工端到端。
- **T5.2** 文档化渐进替换策略（README 节）。

### P6 压测与数据报告（docs/benchmarks/）
- **T6.1** JMH：Redis 计数（redis-mq vs direct-db A/B）、滑动 vs 固定窗口、锁竞争、布隆吞吐/误判。
- **T6.2** HTTP 全链路压测：详情页（缓存命中/穿透/击穿场景）、点赞、搜索，并发爬坡找拐点（工具优先 wrk/k6，不可用则自研 Java 21 虚拟线程 LoadGenerator 提交到 `tools/bench/`）。
- **T6.3** RAG 基准扩充：语料与查询集扩容，Recall@K/MRR/HitRate，hybrid vs dense vs keyword、HNSW vs exact、并发检索压测（复用 `ai-service/app/rag/evaluation/evaluate.py`）。
- **T6.4** mini-MQ vs RabbitMQ 同机对比（吞吐/延迟/消息大小）。
- **T6.5** 报告 md（环境/方法/原始数据/复现命令）写入 `docs/benchmarks/`。
- 验证：每个数字可从仓库脚本复现。

### P7 文档与发布
- **T7.1** README 重写：架构图（archify 生成 HTML 存 docs/images）、模块说明、STAR 亮点清单（问题→方案→数据）、基准数据表、运行指南、测试矩阵。
- **T7.2** 全量回归：`mvnw test`（含全部模块）+ `pytest`。
- **T7.3** 分阶段 commit（每任务 1 commit）→ `git push origin master`。
- **T7.4** 最终交付说明：所有简历亮点改动清单（问题/方案/性能提升数据）。

## 10. Verification（总验收）

- `mvnw clean test`（根聚合，含 4 个 Java 模块）全绿；
- `cd ai-service && pytest` 122+ 全绿；
- `docs/benchmarks/` 每份报告含复现命令且数据与脚本一致；
- README 包含 STAR 亮点清单 + 数据表；
- origin/master 推送成功。

## 11. Risks

- **本机基础设施可用性**（MySQL/Redis/RabbitMQ/Docker）：P2/P5 手工 smoke 与 P6 依赖真实中间件；若 Docker 不可用 → JMH 纯 JVM 基准 + 嵌入式替身继续，HTTP 压测降级为直接服务进程 + H2？——计数 A/B 需真实 MySQL，不可用时明确标注「数据未采集，理由 X」，不编造。
- **范围失控**：以任务清单为界，新需求进 worklog 待议。
- **主项目模块化迁移**：git mv 保持 rename 检测，迁移后立即全量回归。
- **mini-mq 与 RabbitMQ 对比不公平性**：同机、同消息大小、同批大小，报告注明双方版本与配置。

## 12. Retirement

- 旧固定窗口限流实现（INCR+EXPIRE）替换为滑动窗口后删除旧逻辑（无保留）。
- `syncCountToDb` 原 setIfAbsent 锁替换为 RedisLock（行为等价，无保留旧路径）。
- `prompt.count.mode` direct-db A/B 开关保留并文档化（benchmark 复用）。
- RabbitMQ 主链路不退休（渐进替换故事的前提）。

## 13. Execution Route

- **P1/P2/P4/P5/P7：inline**（跨模块契约与主项目集成，需我直接掌控）。
- **P3（mini-mq 核心）：subagent_fork**（独立模块、边界清晰、交付物=测试全绿模块+JMH 报告；我在主线程推进 P2）。若子代理质量不达标则收回 inline。
- **P6：subagent 并行**（JMH/HTTP/RAG 三路独立，各自产出报告后我复核数据真实性）。
- User confirmation required: no — 范围/验收/工作区边界已由用户确认；推送 origin/master 已由用户明确要求。
