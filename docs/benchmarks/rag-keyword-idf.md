# RAG 关键词通道 IDF 过滤改造评测报告

日期：2026-09-24
结论先行：**IDF 过滤把 hybrid 从 R@5=0.500 提升到 0.5359（+7.2% 相对），但仍低于单路 dense 的 0.5738（-6.6%）。混合检索在本语料上仍未超过 dense；唯一稳定超过 dense 的配置是 hybrid + rerank（R@5=0.6456，与历史 0.6413 一致）。关键词通道从「明显拖累」变为「接近 dense 但未反超」——这是诚实的负结论，附完整数据。**

## 1. 背景与问题

上一轮审计确认：`KeywordRetriever` 从 `websearch_to_tsquery`（AND 语义，恒空）改成裸 OR `to_tsquery` 后，自然语言查询里的高频词（the/a/of/and…）命中大量无关 chunk，经 `ts_rank_cd` 放大后把噪声注入 RRF 融合，导致 hybrid R@5=0.500，比 dense（0.5738）**差 12.9%**。

本次改造：在 SQL 之前做**可解释的低区分度词截断**——用 PostgreSQL `ts_stat` 取词级 ndoc 统计近似 IDF，过滤后再交给 `ts_rank_cd` 排序。

## 2. 实现（ai-service/app/rag/retrieval/keyword_retriever.py）

类名与 `retrieve(query, top_k)` 签名不变。新流程：

1. `ts_stat('SELECT search_vector FROM rag_chunk WHERE knowledge_base_id=...')` 取 KB 级词统计（词 → ndoc），带 300s TTL 缓存（ts_stat 是全表扫描，同 KB 的多次查询只付一次代价）；kb_id 经 `[A-Za-z0-9_-]+` 校验 + 单引号转义防注入。
2. 查询词元逐词过滤：
   - ndoc ≥ 60% 语料 chunk 的词 → 丢弃（停用词近似）；
   - 语料中不存在的词 → 丢弃（OR 语义下无贡献）。
3. 剩余词按 IDF 降序最多保留 6 个。
4. 全部被过滤 → 返回空列表，**放弃关键词通道**（hybrid 退化为 dense，避免噪声注入）。
5. 排序仍是 `ts_rank_cd`（保留其 cover density 归一化）。

**为什么不做完整 BM25**（代码注释中同样写明）：PostgreSQL 的 tsvector 不保留词频元数据，候选级 tf 需要额外建词频表并持续维护；本实现用 ts_stat 的 ndoc 做 IDF 预过滤 + ts_rank_cd 打分，是 BM25 的可解释近似，不是伪造的 BM25。

单测（tests/test_rag_retrievers.py，5 个新用例）：低 IDF 词过滤与字段映射 / 最多保留 6 词 / 全滤空放弃通道 / 语料外词丢弃 / 统计 TTL 缓存。全量 `pytest` **126 passed**。

## 3. 环境与方法

| 项 | 值 |
|---|---|
| 数据集 | `rag-eval/datasets/p1.jsonl`，79 条人工标注查询，chunk 级黄金标签 |
| 知识库 | kb-main，892 chunks（容器 `bench-rag-pg`，宿主 5433，rag/rag_password/rag） |
| 词统计 | ts_stat 8680 词 / 502164 entries |
| 嵌入 | Ollama qwen3-embedding:0.6b（1024 维），localhost:11434 |
| 重排 | bge-reranker-v2-m3，CPU 推理 |
| Runner | `docs/benchmarks/scripts/rag_idf_eval.py`（方法学复刻自 ai-prompt-benchmark/scripts/eval_matrix.py：同数据集、同打分、同 warmup 流程） |

**基线可信性验证**：改造前用同一 runner 跑 dense + hybrid（裸 OR），两轮结果 **0.5738 / 0.500，与历史记录完全一致** → 环境重建成功、评测方法学可信，前后对比有效。

## 4. 结果

### 4.1 主表（R@5 / MRR@5，79 查询）

| 配置 | R@5 | MRR@5 | R@10 | MRR@10 | P95 延迟 | 来源 |
|---|---|---|---|---|---|---|
| dense | 0.5738 | 0.4211 | 0.6730 | 0.4339 | ~52ms | 本轮复现（2 轮一致）；历史同值 |
| hybrid（裸 OR，改造前） | 0.5000 | 0.3677 | 0.6561 | 0.3891 | ~57ms | 本轮基线复现（2 轮一致）；历史同值 |
| **hybrid（IDF 过滤，本次）** | **0.5359** | **0.3989** | **0.6646** | **0.4198** | ~58ms | 本轮，2 轮稳定 |
| hybrid（IDF）+ rerank | 0.6456 | 0.5057 | 0.7342 | 0.5122 | ~23.8s | 本轮仅 run1（见 4.2 说明）；历史 0.6413/0.5095 参考 |

### 4.2 数据完整性说明（诚实标注）

- **hybrid+rerank 第 2 轮被时限终止**：rerank 为 CPU 推理（中位 ~14.4s/查询，79 查询 ≈ 19 分钟/轮），第 2 轮未完成即被停止。表中 rerank 行仅 run1。
- **测量期间存在并发负载**：rerank 轮次执行期间，同一台机器上运行了约 5 分钟的 HTTP 压测（主线程的计数/点赞拐点实验）。质量指标（Recall/MRR）不受影响，但 **rerank 的延迟数字仅供参考**（P95 23.8s 与历史 22.2s 量级一致，不能排除被并发负载拉高）。
- 最终轮次的 per-query CSV 因 runner 在所有模式完成后才落盘而缺失；改造前基线 CSV（`raw/rag_idf_baseline_per_query.csv`）完整可用。

### 4.3 查询行为分析（解释 hybrid 为何只进步不反超）

`raw/rag_idf_query_analysis.json`：

| 指标 | 值 |
|---|---|
| 全滤空、放弃关键词通道的查询 | 5 / 79（6.3%） |
| 平均保留词数 | 5.08（62 条查询触顶 6 词上限） |
| 被丢弃最多的词 | and(32) / the(23) / a(14) / to(10) / of(10) |

停用词注入的噪声被精确切除——这正是 0.500 → 0.5359 的来源。但剩余差距说明：**在本语料上，关键词通道的增量信号仍不足以在 RRF 融合中压过 dense 的语义信号**，RRF 的 rank 融合对「词面命中但语义不相关」的候选仍然敏感。

## 5. 结论

1. **改进是否转正？部分转正**：hybrid 与 dense 的差距从 -12.9% 收窄到 -6.6%（0.500 → 0.5359，两轮稳定复现），关键词通道不再明显拖累融合。
2. **混合检索能否超过单路 dense？本语料不能**。需要 rerank 后处理才能反超（0.6456，与历史一致），而 rerank 的 CPU 代价（~14s/查询）使其只适合离线/低 QPS 场景——这也是历史报告的既有结论，本次未推翻。
3. **代价**：每条查询新增一次统计查表（dict 查找，可忽略）；每 KB 每 300s 一次 ts_stat 全表扫描（892 chunks 规模下未单独计时，冷启动成本被评测 warmup 摊薄——**未单独测量是本次评测的已知空白**）；5 条查询因全滤空而完全放弃关键词通道（由 dense 兜底，无副作用）。
4. **工程价值**：这个负结论本身是可信的亮点素材——用数据证明了「混合检索不是银弹」，并给出了可解释的噪声切除手段（IDF 截断）与边界（低区分度查询自动退化为 dense 兜底）。

## 6. 复现命令

```powershell
# 前置：历史评测库容器（若已停止）
docker start bench-rag-pg   # 映射宿主 5433

# 全量单测（126 passed）
cd ai-service && .\.venv\Scripts\python.exe -m pytest tests -q

# 基线矩阵（改造前裸 OR，2 轮）
.\.venv\Scripts\python.exe docs\benchmarks\scripts\rag_idf_eval.py `
  --dataset D:\Code\java_program\ai-prompt-benchmark\rag-eval\datasets\p1.jsonl `
  --runs 2 --out-prefix docs\benchmarks\raw\rag_idf_baseline --modes dense hybrid_rrf

# 最终矩阵（IDF 版，2 轮；rerank 仅建议 runs 1，约 19 分钟/轮）
.\.venv\Scripts\python.exe docs\benchmarks\scripts\rag_idf_eval.py `
  --dataset D:\Code\java_program\ai-prompt-benchmark\rag-eval\datasets\p1.jsonl `
  --runs 1 --out-prefix docs\benchmarks\raw\rag_idf_final --modes dense hybrid_rrf hybrid_rerank

# 查询过滤行为分析
.\.venv\Scripts\python.exe docs\benchmarks\scripts\rag_idf_query_analysis.py
```

## 7. 原始数据

| 文件 | 内容 |
|---|---|
| `raw/rag_idf_baseline_matrix.json` + `rag_idf_baseline_per_query.csv` | 改造前基线（dense/hybrid 裸 OR，2 轮，逐查询明细） |
| `raw/rag_idf_final_matrix.json` | 本次最终矩阵（含 rerank 未完成/并发负载的 meta 说明） |
| `raw/rag_idf_query_analysis.json` | 79 条查询的 IDF 过滤行为统计 |
