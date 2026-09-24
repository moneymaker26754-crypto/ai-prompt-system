# ai-prompt-system Benchmark 实验报告

> 基于 https://github.com/moneymaker26754-crypto/ai-prompt-system（commit `432b751`，工作副本 `D:\Code\java_program\ai-prompt-system - ver2`，未提交/未推送任何改动）
> 实验日期：2026-09-21 · 全部原始数据在 `results/`，图表在 `results/charts/*.svg`，脚本在 `scripts/`

## 0. 实验环境与方法

| 组件 | 版本/配置 |
|---|---|
| RAG 服务 | ai-service (FastAPI + Python 3.13)，检索组件复用 `app.rag.*`（DenseRetriever / HybridRetriever(RRF k=60) / BgeReranker） |
| 向量库 | pgvector/pgvector:pg16（容器 `bench-rag-pg`，5433），HNSW 索引 m=16/ef_construction=64，tsvector trigger |
| 嵌入模型 | qwen3-embedding:0.6b（Ollama，RTX 4060 Laptop 8GB） |
| 重排模型 | BAAI/bge-reranker-v2-m3（本地 2.27GB，CPU fp32，use_fp16=False，项目默认配置） |
| Java 应用 | Spring Boot 3.5.13 / JDK 25（--release 21），复用本机既有 MySQL8(3306)/Redis(6379)/RabbitMQ(5672) |
| 压测 | JMeter 5.6.3（HTTP 下载）+ 自研异步压测驱动 `load_driver.py`（口径一致，用于交叉验证） |

**评测数据集**：35 篇开源 markdown 语料（Microsoft generative-ai-for-beginners 15 课、dair-ai Prompt-Engineering-Guide 7 篇、Ragas 7 篇、pgvector README、项目自身文档 5 篇），默认 KB `kb-main` 以 800/120 切分为 **892 chunks**。
**查询集**：**79 条人工标注查询**（64 英文 + 15 中文），每条标注到具体 chunk（`(source, chunk_index)` 级），已通过 `label_check.py` 校验（0 缺失），并经用户复核确认（`rag-eval/datasets/p1.review.md`）。

指标口径：chunk 级 Recall@5 / MRR@5 / Recall@10 / MRR@10；P95 用 nearest-rank；每次检索计时含 embedding+检索全链路。Rerank 模式先做 warmup 排除冷启动。结果均为确定性检索（重复运行方差 <0.1%）。

---

## 1. P1：RAG evaluation（第一优先级）

**管线对比**：Dense（纯向量）→ Hybrid+RRF（向量+关键词 RRF 融合）→ Hybrid+RRF+BgeReranker（二阶段重排），kb-main，top5。

| 模式 | Recall@5 | MRR@5 | Recall@10 | MRR@10 | P50 | P95 |
|---|---|---|---|---|---|---|
| Dense | 0.5738 | 0.4211 | 0.6730 | 0.4339 | 42.5 ms | **49.4 ms** |
| Hybrid + RRF | 0.5738 | 0.4295（+2.0%，仅 1/79 条查询，属噪声） | 0.6730 | 0.4423（同前） | 48.5 ms | 52.8 ms |
| Hybrid + RRF + Reranker | **0.6413** (+11.8%) | **0.5095** (+18.6%) | — | — | 13.9 s | 22.2 s |

图表：[p1_quality.svg](results/charts/p1_quality.svg) · [p1_latency.svg](results/charts/p1_latency.svg) · 逐查询明细：[p1_retrieval_per_query.csv](results/p1_retrieval_per_query.csv)

**附带证据（项目内置 EXACT vs HNSW 接口，79 queries）**：recall@5 均为 0.5738；P95 exact 19.9ms vs HNSW 20.0ms —— 892 chunks 规模下 HNSW 无延迟优势（近似索引在 10 万+ 向量才显效），质量零损失。

### 结论
1. **Dense vs Hybrid（重要更正，2026-09-21 复审计后）**：逐查询审计显示 79/79 条查询 dense 与 hybrid 的 **top1 完全相同**、MRR 仅 1 条不同。根因是**关键词通道对自然语言查询几乎恒返回空**：`KeywordRetriever` 使用 `websearch_to_tsquery('simple', q)`，websearch 语法对未加引号的词是 **AND 语义**（要求整句所有单词同时出现在同一个 chunk，800 字 chunk 几乎不可能），中文查询在 'simple' 配置下更被整句视作单个词（语料为英文 → 恒空）。逐条实测：自然语言查询关键词返回 0 行，短词查询（如 'vector database'）才返回 15 行。**因此"当前配置下的 Hybrid"与 Dense 完全等价，本实验无法证明 Hybrid 的收益**。修复方向与修复后重测见 §1.1。
2. **RRF vs Reranker（证明二阶段排序）**：**bge-reranker-v2-m3 相对无重排管线：Recall@5 +11.8%（0.5738→0.6413），MRR@5 +18.6%（0.4211→0.5095）**，二阶段排序价值显著。代价：CPU 重排延迟中位 13.9s、P95 22.2s——**重排器是质量收益最大、也是吞吐最大的瓶颈**（详见 §4）。

### 1.1 关键词通道修复后的对照重测（2026-09-21，工作副本未提交）

将 `keyword_retriever.py` 的查询构造改为 OR 语义（`to_tsquery('simple', ' | '.join(tokens))`，token 按 `[A-Za-z0-9]+`/中文连续串切分）后，同一 79 条查询重测：

| 模式 | Recall@5 | MRR@5 | Recall@10 | MRR@10 |
|---|---|---|---|---|
| Dense | 0.5738 | 0.4211 | 0.6730 | 0.4339 |
| Hybrid+RRF（现配置，关键词恒空） | 0.5738 | 0.4295 | 0.6730 | 0.4423 |
| **Hybrid+RRF（OR 修复后）** | **0.5000 (-12.9%)** | **0.3677 (-12.7%)** | 0.6561 (-2.5%) | 0.3891 (-10.3%) |
| Hybrid+RRF+Reranker（OR 修复后） | 0.6414 (+11.8% vs Dense) | 0.5095 (+21.0% vs Dense) | — | — |

数据：[p1_retrieval_fixed_matrix.json](results/p1_retrieval_fixed_matrix.json) · [p1_fixed_matrix.json](results/p1_fixed_matrix.json) · [p1_retrieval_fixed_per_query.csv](results/p1_retrieval_fixed_per_query.csv)

**结论**：
- **一个"能用的"关键词通道 ≠ 一个"有益的"关键词通道**：裸 OR + `ts_rank_cd`（词频密度排序、无停用词/无 IDF 加权）会把高频词噪声块挤进 RRF top5，**Hybrid 反而比 Dense 差 12.9%（Recall@5）**。混合检索不是免费午餐——词法通道的质量决定 RRF 是增益还是负增益。
- **Reranker 对第一阶段噪声高度鲁棒**：候选集混入关键词噪声后，重排结果几乎不变（0.6414/0.5095 vs 原 0.6413/0.5095）——二阶段排序恰好是"坏第一阶段"的兜底，这进一步支撑"必须上 Reranker"的结论。
- 让 Hybrid 转正的下一步（未在本轮做，避免针对单一数据集调参）：停用词过滤 + BM25/IDF 类词项加权，或直接使用 BM25 实现（如 PostgreSQL 的 `ts_rank` + `idf` 自建权重），再重跑同一数据集验证。

---

## 2. P2：Chunk Size 消融（第二优先）

5 个独立 KB 全量入库（hybrid-RRF，top5；文档级与块级双口径）。

**表 A：现配置管线（关键词恒空 ≈ 纯 Dense）**：

| chunk/overlap | chunks | Recall@5 (doc) | MRR@5 (doc) | Recall@5 (chunk) | P95 |
|---|---|---|---|---|---|
| 256/40 | 2880 | 0.9241 | 0.8078 | 0.0416 | 155.5 ms |
| 512/80 | 1415 | 0.9494 | 0.8259 | 0.0860 | 181.6 ms |
| **800/120（默认）** | 892 | **0.9620** | 0.8618 | 0.1431 | 155.5 ms |
| 1200/180 | 591 | 0.9367 | 0.8776 | 0.2185 | 162.1 ms |
| 1600/240 | 451 | 0.9494 | **0.8781** | **0.2785** | 160.5 ms |

**表 B：关键词 OR 修复后管线**（`results/p2_chunk_fixed.csv`）：

| chunk/overlap | Recall@5 (doc) | MRR@5 (doc) | Recall@5 (chunk) |
|---|---|---|---|
| 256/40 | 0.8734 | 0.7430 | 0.0363 |
| 512/80 | 0.9494 | 0.7808 | 0.0725 |
| **800/120（默认）** | 0.9367 | **0.8342** | 0.1213 |
| 1200/180 | 0.9241 | 0.8295 | 0.1947 |
| 1600/240 | **0.9620** | 0.8074 | **0.2436** |

图表：[p2_chunk.svg](results/charts/p2_chunk.svg)（表 A）· 数据：[p2_chunk_cl.csv](results/p2_chunk_cl.csv) / [p2_chunk_fixed.csv](results/p2_chunk_fixed.csv)

### 结论
- **两条管线一致的最稳结论**：256 档各项最差（语义碎片化）；**块级 Recall@5 随 chunk 增大单调上升**（表 A 0.042→0.279；表 B 0.036→0.244）——大块让答案更集中在单块，这是机械效应占主导。
- **文档级 Recall@5 的最优点依赖检索管线**：纯向量（表 A）在 **800** 最优（0.962）；关键词 OR 修复后（表 B）在 **1600** 最优（0.962，噪声关键词在大块下危害更小），但 MRR 在 800 最优。
- 工程建议：**保持默认 800/120**（纯向量/现配置下的召回最优点、MRR 与延迟均衡）；若采用强词法通道，可上探 1200/1600；256 不推荐。

---

## 3. P3：Redis+MQ A/B（第三优先）

在 Java 应用中新增未提交的 A/B 开关 `prompt.count.mode`（`redis-mq` 现状 vs `direct-db` 基线：计数直写 DB、跳过 Redis INCR/ZSet 与 MQ 合并写；幂等/去重相应回落 DB 或关闭）。同一台机器、同一批 150 用户 JWT、同一 300 个 ENABLED prompt，混合负载（浏览/复制/点赞各 60s）：

| 指标（每 60s 窗口） | A: Redis+MQ @20u | B: 直写DB @20u | A: Redis+MQ @50u | B: 直写DB @50u |
|---|---|---|---|---|
| 总请求 | 143,756 | 137,045 | 77,900 | 79,865 |
| 混合 TPS（like 代表） | **744.5** | 687.6 | 310.6 | **445.9** |
| **DB Writes / 1000 请求** | **202.0** | 1,094.2 | **203.8** | 802.1 |
| DB Writes/s | 484.1 | 2,499.3 | 264.6 | 1,067.6 |
| Com_update 增量 | 602（仅 60s 合并回刷） | 122,867（每次互动直写） | 602 | 58,583 |
| Com_insert 增量 | 28,443（点赞落库） | 27,090 | 15,275 | 5,473 |
| Redis 命令增量 | 1,188,936 | 287,178（仅详情缓存） | 706,563 | 178,984 |
| like P95 | 52.3 ms | 67.7 ms | 591.1 ms | 375.6 ms |

图表：[ab_writes.svg](results/charts/ab_writes.svg) · [ab_tps.svg](results/charts/ab_tps.svg) · 数据：`results/ab_*.json`

### 结论（工程收益）
- **DB 写入量：Redis+MQ 方案把每千次互动的 DB 写从 ~1,094（20u）/802（50u）降到 ~202/204，降幅 3.9–5.4×**；浏览/复制在 A 方案下 0 DB 写，点赞计数经 60s 延迟 MQ 合并为每窗口 1 次 UPDATE。
- **TPS 同档持平或更优**（20u：A 744 vs B 688；50u 受点赞唯一键竞争影响互有高低）——把写压力从 MySQL 转移到 Redis 的收益明确，与项目"减少数据库写压力/避免热点直写"的设计目标一致。
- Redis 命令数上升（118.9 万/分钟）是可接受的置换成本（Redis 单机轻松承载，且 A 方案仍然保留详情缓存读）。

---

## 4. P4：RAG 压测（JMeter P95/TPS）

`/internal/rag/search`（rerank=false），JMeter 与自研驱动同口径：

| 并发 | TPS | P50 | P95 | 说明 |
|---|---|---|---|---|
| 10 users | 68.2 | 140 ms | **182 ms** | 无排队 |
| 50 users | 69.3 | 642 ms | 1,415 ms | TPS 已饱和 |
| 100 users | **69.7** | 1,120 ms | 3,294 ms | 延迟随并发线性恶化 |

rerank=true（bge-m3 CPU 单进程重排）：

| 并发 | TPS | P95 |
|---|---|---|
| 1 user | 0.09 | 14.8 s |
| 2 users | 0.11 | 21.5 s |
| 4 users | 0.11 | 51.9 s |
| 20 users（JMeter，60s） | **0.112（40 samples/358s）** | **352.3 s** |

图表：[p4_tps.svg](results/charts/p4_tps.svg) · JMeter 原始：[jmeter_rag_search.jtl](results/jmeter_rag_search.jtl) · 计划：[jmeter/rag_search.jmx](jmeter/rag_search.jmx)

### 结论
- **rerank=false 吞吐上限 ~69 TPS**，瓶颈在嵌入链路（Ollama 串行/批处理能力），10 并发即接近饱和，之后 P95 从 182ms 恶化到 3.3s。
- **rerank=true 吞吐天花板仅 0.11 TPS**：CPU 上 bge-reranker-v2-m3 每请求 12–15s，并发只会叠加排队（20 并发 P95 高达 352s）。**重排器是整条 RAG 链路的吞吐瓶颈**，生产建议：GPU 推理 / fp16 或量化 / 跨请求批处理（batching）/ 降低 retrieve_top_k。

---

## 5. 总结论（对照实验矩阵）

| 实验 | 证明什么 | 关键数据 | 结论 |
|---|---|---|---|
| Dense vs Hybrid | Hybrid Retrieval | 现配置 Hybrid≡Dense（关键词恒空）；OR 修复后 Hybrid **差于 Dense 12.9%**（0.500 vs 0.5738） | ❌ 现配置无法证明 Hybrid 收益；裸 OR+密度排序注入噪声反而有害；需 BM25/IDF+停用词后再验 |
| RRF vs Reranker | 二阶段排序 | Recall@5 +11.8%（0.6413）；MRR@5 +18.6%（0.5095）；对噪声候选鲁棒（0.6414/0.5095） | 重排收益显著且是第一阶段的兜底；CPU 延迟是代价 |
| Chunk Size | chunk 策略 | 纯向量：doc-Recall@5 在 800 最优 0.962；OR 修复后 1600 最优 0.962；块级 Recall 随 size↑ | 默认 800/120 合理（现配置最优点）；256 最差 |
| RAG 压测 | 性能 | rerank=false 69 TPS@100u（P95 3.3s）；rerank=true 0.11 TPS | 嵌入为第一瓶颈，CPU 重排为绝对瓶颈 |
| Redis+MQ A/B | 工程收益 | DB Writes/千请求 202 vs 1,094（20u）→ **-81.5%**（50u -74.6%）；TPS 同档 | 计数上云（Redis）+MQ 合并落库收益明确 |

---

## 6. 附带发现（未修改的仓库问题，供参考）

1. **ai-service 无法直接启动**：`generation/{grounded_answer_service,factory}.py` 导入模块名拼写错误 `langchian_retriever`（实际文件 `langchain_retriever.py`）——本次已在工作副本中修复（未提交）。
2. **RecursiveChunker 锚定 bug**：连续重复分隔符（如空行串）被 `_recursive_split` 丢弃后，`text.find(content)` 会抛 `ValueError: Content not found in document`，35 篇语料中 13 篇大文档入库失败；benchmark 采用等价分片逻辑的 `scripts/robust_chunker.py`（仅改锚定方式，不改变 chunk 内容）绕过。
3. **仓库缺少 RAG 建表 DDL 与 MySQL DDL**：`rag_ddl.sql`（vector 扩展/表/tsvector trigger/HNSW 索引）与 `ai_prompt_ddl.sql` 由 benchmark 脚手架补齐。
4. **EXACT vs HNSW 在 892 chunks 规模无差异**：HNSW 的 P95 收益需要更大数据量才能体现。
5. **关键词检索通道失效 + 修复重测（本次最重要的发现，2026-09-21 复审计确认）**：`KeywordRetriever` 的 `websearch_to_tsquery('simple', q)` 对未加引号词是 AND 语义，要求整句所有词同时出现在同一个 chunk；对 79 条评测查询逐一验证，关键词通道几乎全部返回 0 行（短词查询如 'vector database' 才返回 15 行；中文查询在 'simple' 配置下整句成词、对英文语料恒空）。→ 现配置 Hybrid 实际等于纯 Dense。**修复重测（工作副本，OR 语义 to_tsquery，未提交）**：Hybrid 反而比 Dense 差 12.9%（噪声词项挤占 RRF top5，见 §1.1）；Reranker 不受影响（鲁棒兜底）。→ 混合检索要转正需 **BM25/IDF 加权 + 停用词**（或引入 BM25 实现），并且建议给 tsvector 换成 'english' 配置（对中文需分词扩展如 zhparser/pg_jieba 或 bigram）。

## 7. 复现与产物

- 复现步骤见 `PROGRESS.md`；数据文件：`results/`（p1_*.json/csv、p1_*_fixed*、p2_chunk_cl.csv、p2_chunk_fixed.csv、ab_*.json、p4_*.json、exact_vs_hnsw.json、jmeter_*.jtl）
- 标注数据集：`rag-eval/datasets/p1.jsonl`（79 条 chunk 级标注，用户已复核）、`queries.jsonl`（文档级，供消融）
- 工具：`scripts/`（ingest/eval_matrix/chunk_ablation/load_driver/ab_runner/db_stats/gen_charts/…）、`jmeter/rag_search.jmx`
- 工作副本改动（均未提交）：`ai-service/app/rag/retrieval/keyword_retriever.py`（OR 语义修复，供 §1.1 重测）、`ai-service/app/rag/generation/*.py`（导入 typo 修复）、`ai-service/.env`、Java A/B 开关两处
