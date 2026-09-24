# 计数链路并发拐点实验（2026-09-24）

> 数据：`raw/knee_*.json`（load_driver 压测驱动，仓库内 `docs/benchmarks/scripts/load_driver.py`）。
> 环境：Windows + OpenJDK 25；app 单实例 dev 配置（Tomcat 默认 200 线程、Druid max-active=20、Lettuce max-active=8、RabbitMQ 计数合并链路 redis-mq 模式）；本机 Docker MySQL 8/Redis 7/RabbitMQ 4。
> 方法：每档 30s 混合压测，view 为公开接口（8 个随机 ENABLED prompt id），like 携带 JWT（与上一轮 A/B 同驱动）。压测期间同一机器上 RAG 重排评测（CPU 推理）并行运行——数字可能存在 ±10% 级别干扰，结论取趋势而非绝对值。

## 结果

| 接口 | 并发 | TPS | p50 (ms) | p95 (ms) | p99 (ms) |
|---|---|---|---|---|---|
| view | 50 | 217.2 | 121.8 | 755.6 | 1324.7 |
| view | 100 | **229.7** | 270.0 | 1381.4 | 2716.3 |
| view | 200 | 187.5 | 656.2 | 3646.8 | 6402.3 |
| view | 400 | 116.1 | 2488.5 | 11340.8 | 14669.4 |
| like | 100 | 161.6 | 399.1 | 2006.0 | 3315.5 |
| like | 200 | 204.5 | 584.9 | 3254.1 | 6290.3 |

## 拐点定位与瓶颈归因

1. **view 拐点在 100~200 并发之间**：TPS 峰值 229.7@100u，200u 即回落（-18%），400u 塌陷至峰值一半且 p99 14.7s——典型的资源池饱和而非 CPU 打满。
2. **证据（压测中采样）**：MySQL 活跃连接 **16/20**（Druid 池顶），Redis 客户端 **10/8**（Lettuce 池超配排队），Tomcat 线程 299（200 默认 + 排队）。
3. **瓶颈链**：单次 view = 缓存读 + 计数 INCR/ZINCRBY + 2 次集合成员判定 + MQ 去重投递（均走 8 连接的 Lettuce 池）→ Redis 连接池先行排队 → 请求持有时间拉长 → Druid 20 连接被占满 → 线程池排队 → 延迟雪崩。
4. **可行动项（已在代码注释标注，未实施，诚实声明）**：
   - Lettuce 池 8→32 并加连接获取超时监控（最小改动，预期显著抬升拐点）；
   - 计数写入 pipeline 合并（JMH 参考值：批量 INCR ≈ 单条 15×）；
   - like 路径的 isLiked/isFavorite 双查合并为一次 Lua 判定。

## 与上一轮 A/B 的关系

上一轮 20/50u A/B（`report-2026-08-rag-counting.md`）回答「Redis 合并写 vs 直写 DB 谁优」；本实验回答「当前配置下系统能扛到多少、卡在哪」——两者互补，共同构成计数链路的完整数据面。
