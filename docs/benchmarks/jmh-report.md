# JMH 微基准报告：计数与分布式组件（2026-09-24）

> 数据来源：`benchmarks/` 模块（JMH 1.37），原始结果 `raw/jmh-results-2026-09-24.json`。
> 环境：Windows + OpenJDK 25.0.2；Redis 7（Docker `my-redis`，localhost:6379）；MySQL 8.4（Docker `mysql8`，localhost:3306，库 ai_prompt，专用表 bench_count，200 行）。
> 方法：`Mode.Throughput`，fork=1，warmup 3×2s，measurement 5×3s。单线程（除锁竞争组为 4 线程）。
> 复现：`.\mvnw.cmd -q -pl benchmarks -am package -DskipTests` 后
> `java -cp "benchmarks/target/benchmarks-0.0.1-SNAPSHOT.jar;benchmarks/target/lib/*" org.openjdk.jmh.Main -f 1 -rf json -rff jmh.json "com.jojo.prompt.bench."`

## 结果

| 基准 | 场景 | 吞吐 (ops/s) | ± 误差 |
|---|---|---|---|
| MysqlCountBenchmark.singleRowUpdate | 计数直写 DB（单行 UPDATE，autocommit） | 180.6 | 48.5 |
| RedisCountBenchmark.fixedWindowIncr | Redis INCR（改造前固定窗口限流稳态） | 914.4 | 188.5 |
| RedisCountBenchmark.slidingWindowLua | 滑动窗口限流判定（ZSET+Lua 原子） | 728.5 | 166.4 |
| RedisCountBenchmark.incrPipelinedBatch50 | pipeline 批量 INCR，50 条/批（参考值） | 274.0/批 | 61.5 |
| RedisCountBenchmark.zsetIncrementScore | 热度榜 ZINCRBY | 916.2 | 215.4 |
| RedisLockBenchmark.uncontendedLockUnlock | 分布式锁获取+释放（无竞争） | 361.3 | 169.9 |
| RedisLockBenchmark.contended | 4 线程竞争同一把锁（组总吞吐） | 2167.0 | 387.8 |
| BloomBenchmark.add | 布隆 add（pipeline，k=7 位） | 763.5 | 152.9 |
| BloomBenchmark.mightContain | 布隆存在性判定（pipeline，k=7 位） | 749.7 | 121.3 |

## 结论与用途

1. **Redis INCR ≈ 5.1× 于 MySQL 单行 UPDATE（914 vs 181 ops/s）**：主项目把点赞/收藏/浏览/复制计数放 Redis、延迟合并落库的微观依据；对应全链路 A/B（见 `report-2026-08-rag-counting.md`）中 redis-mq 优于 direct-db 的写路径解释。
2. **pipeline 批量 INCR ≈ 15× 于单条 INCR（274 批/s × 50 = ~13.7k INCR/s）**：说明「合并写」的收益上限。当前计数路径仍是单条 INCR（每互动 1 次 RTT），这是后续优化点，本行数据仅作参考值，不宣称已实现。
3. **滑动窗口限流比固定窗口慢约 20%（728 vs 914）**：以 20% 吞吐换窗口边界精确（ZSET 维护成本）。多实例/高并发下两者都受 Redis 单实例上限约束；限流属前台低 QPS 场景，20% 代价可接受。
4. **布隆 ~750 ops/s（本地 Redis，单客户端）**：k=7 次位操作经 pipeline 合并为 1 RTT；对比非 pipeline 需 7 次 RTT（约 7× 延迟）。误判率实测见 infra-starter 集成测试（≤ 理论值 2 倍裕量）。
5. **锁**：无竞争 361 对/s；4 线程竞争下组总吞吐 2167/s（未观察到线程饥饿）。锁路径为 2 条 Lua（获取+释放），与单条 INCR 同数量级。

## 诚实边界

- 单机单客户端，误差区间较宽（Windows 背景噪声 + 容器共享 CPU），数字用于**相对比较**而非绝对容量承诺。
- `singleRowUpdate` 为 autocommit 单条更新；真实 direct-db A/B 的全链路数字见历史报告。
- 所有基准代码在仓库内，任何数字可一键复现。
