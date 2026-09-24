# WORKLOG — 企业级改造与纯技术组件（2026-09-24）

状态载体：durable work record（long-task-continuation）。父计划：`docs/aegis/plans/2026-09-24-enterprise-hardening.md`。

## Intent Lock

- 目标：见父计划 §1 Goal（加固 + mini-MQ + 双 starter + 基准数据 + README + push origin/master）。
- 范围锁：不做微服务拆分/网关/注册中心/K8s/DB 迁移/前端。
- 验收锁：`mvnw clean test` 全模块绿 + pytest 绿 + benchmarks 可复现 + README STAR 清单 + push 成功。

## Baseline Lock

- HEAD（开工）：`6144f0a`（= d27e04e + 776b57a + 6144f0a 于 27a70b8 之上）。
- 双端测试基线全绿实测：
  - Java `mvnw.cmd -q test` → `MVN_EXIT=0`（15 测试类；surefire 已加 `-Djdk.net.URLClassPath.disableClassPathURLCheck=true` 修 JDK 25 fork 崩溃）。
  - Python `pytest tests -q` → `122 passed`。
- 环境事实：JAVA_HOME = OpenJDK 25.0.2（pom release=21）；ai-service venv 在 `ai-service/.venv`。

## Todo Map

- [x] P0 基线修复 + 3 commit（d27e04e/776b57a/6144f0a）+ .gitignore pycache
- [x] T2.1 多模块骨架（commit 6bc0c59；git 保留 rename 历史）
- [x] P1 infra-starter（T1.1–T1.6，commit e9dc350；**修正**：初版集成测试因 Lettuce 工厂未 start 而静默跳过，已在 commit（fix: tests were silently skipped）修复并重跑——20/20 全绿打真实 Redis：lock 7 / rate-limit 4 / idempotent 3 / bloom 6）
- [x] P2 主项目接入（commit 5542917：限流委托/计数锁替换/confirm 幂等/布隆+互斥重建/copy @RateLimit/异常处理 + 3 个缓存三防切片测试；app 全绿）
- [x] P3 mini-mq 模块（commit 9328f26；subagent 81940238 交付，独立验收 14/14 全绿）
- [x] P4 mini-mq-spring-boot-starter（commit 3780b54 内含；starter 端到端测试 1/1 绿）
- [x] P5 主项目接入 mini-MQ（commit 3780b54；行为日志/通知经 mini-MQ + 本地降级，prompt.mq.mode 开关）
- [~] P6.1 JMH 完成（jmh-report.md + 9 项数据）
- [~] P6.3 RAG：subagent a3d9b9ad 最后矩阵运行中
- [~] P6.4 mini-MQ vs RabbitMQ 同机对比（进行中）
- [ ] P6.2 HTTP 全链路高并发找拐点（上一轮数据在 docs/benchmarks/raw/，100u+ 扩展待做）
- [ ] P7 README + 全量回归 + push + 亮点清单

## Current Checkpoint

- 活跃切片：P6.1（JMH 运行准备）+ P3/P6.3 两个子代理并行。
- 分支/HEAD：master @ f938a7f + 未提交（benchmarks 模块、docs/benchmarks 资产、worklog）。
- 阻塞：无。
- 关键资产：兄弟仓库 D:\Code\java_program\ai-prompt-benchmark 的历史数据（RAG 矩阵/计数 A/B/压测）已收编 docs/benchmarks/；历史结论 dense R@5=0.5738、裸 OR hybrid=0.500（负结果，已记录），IDF 改进在途。
- 经验教训（重要，已记入代码）：根 pom pluginManagement 的 maven-compiler-plugin 配置里加 `<parameters>` 会破坏与 spring-boot-parent 的配置合并、导致 Lombok 注解处理器失效——参数名功能本就由 `maven.compiler.parameters` 提供，勿再在 plugin 配置里重复声明。
- 设计决策记录（供 resume/README 引用）：
  - confirm 幂等用 SETNX 占位即可串行化并发，因此不再叠加 RedisLock（避免冗余，AGENTS 简化原则）；
  - starter 不依赖 servlet/security：IP/USER 维度走 RequestDimension SPI，app 侧实现；
  - 布隆预热（ApplicationReadyEvent）防冷启动误 404，未预热时查询链路 fail-open；
  - 互斥重建采用「锁 + 二次读缓存」单飞模式，竞争者未抢到锁时直连 DB（允许极端竞争下多一次 DB 读，换取实现简单）。

## Evidence Trail

- P1 证据：infra-starter **20/20 全绿打真实 Redis**（lock 7 / rate-limit 4 / idempotent 3 / bloom 6；已修复"静默跳过"验证漏洞并记入 commit 2a75af3）。
- P2 真机证据（2026-09-24 12:55 冒烟）：应用 dev 配置启动 11.8s；RabbitMQ admin@5672 连接成功；布隆预热 1500 prompts / 3947ms / 位图 117KB（m=958506, k=7）。
- P6.1 证据：JMH 9 项基准落盘 docs/benchmarks/（jmh-report.md + raw/jmh-results-2026-09-24.json）。
- 环境事实：本机 Docker 容器全在（mysql8/my-redis/rabbitmq），Redis/MySQL/RabbitMQ 端口可用 → P6 压测可做真实中间件对比。

## Resume Hint

下次恢复：读父计划 §9 与本节，跑 `git status` 对照 HEAD=6144f0a，再跑一次 `mvnw -q test` 确认干净，然后从 Current Checkpoint 继续。
