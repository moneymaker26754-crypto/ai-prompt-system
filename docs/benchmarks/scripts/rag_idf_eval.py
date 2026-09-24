"""P1 RAG retrieval matrix runner（适配自 ai-prompt-benchmark/scripts/eval_matrix.py）。

用途：在 ai-service 关键词检索改造前后各跑一次，产出可对比的指标：
dense / hybrid_rrf（Dense + 本仓库 KeywordRetriever）/ hybrid_rerank（hybrid + BgeReranker）。

方法学与历史评测保持一致（同数据集 p1.jsonl、同 kb-main、同打分逻辑），
保证与本报告引用的历史基线（dense R@5=0.5738 / 裸OR hybrid R@5=0.500）可比。

用法（在本仓库根目录运行）：
  python docs/benchmarks/scripts/rag_idf_eval.py \
      --dataset D:/Code/java_program/ai-prompt-benchmark/rag-eval/datasets/p1.jsonl \
      --runs 2 --out-prefix docs/benchmarks/raw/rag_idf_baseline \
      --modes dense hybrid_rrf hybrid_rerank
"""
import argparse
import asyncio
import csv
import json
import statistics
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
AI_SERVICE_ROOT = REPO_ROOT / "ai-service"
sys.path.insert(0, str(AI_SERVICE_ROOT))

import httpx  # noqa: E402

from app.clients.ollama import OllamaClient  # noqa: E402
from app.core.config import get_settings  # noqa: E402
from app.database import create_async_engine, create_session_factory  # noqa: E402
from app.rag.embedder import EmbeddingService  # noqa: E402
from app.rag.retrieval.dense_retriever import DenseRetriever  # noqa: E402
from app.rag.retrieval.hybrid_retriever import HybridRetriever  # noqa: E402
from app.rag.retrieval.keyword_retriever import KeywordRetriever  # noqa: E402
from app.rag.retrieval.reranker import BgeReranker  # noqa: E402
from app.rag.retrieval.retrieval_service import RetrievalService  # noqa: E402
from app.rag.retriever import RetrievalQuery  # noqa: E402
from app.rag.schemas import EvaluationCase  # noqa: E402

TOP_K = 5
RETRIEVE_TOP_K = 20
MODES = ("dense", "hybrid_rrf", "hybrid_rerank")


def load_cases(path: Path) -> list[EvaluationCase]:
    cases = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            if line.strip():
                cases.append(EvaluationCase.model_validate_json(line))
    if not cases:
        raise ValueError("empty dataset")
    return cases


def pct(values: list[float], q: float) -> float:
    ordered = sorted(values)
    idx = min(len(ordered) - 1, max(0, round(q / 100 * (len(ordered) - 1))))
    return ordered[idx]


def score_at_k(case: EvaluationCase, results: list, k: int) -> dict:
    relevant = {(c.source, c.chunk_index) for c in case.relevant_chunks}
    retrieved = [(r.source, r.chunk_index) for r in results[:k]]
    recall = len(set(retrieved) & relevant) / len(relevant)
    rr = 0.0
    for rank, chunk in enumerate(retrieved, start=1):
        if chunk in relevant:
            rr = 1.0 / rank
            break
    return {"recall": recall, "rr": rr, "hit": int(rr > 0)}


async def run_once(cases, dense, hybrid, reranker, service, mode) -> tuple[dict, list[dict]]:
    rows = []
    latencies = []
    recalls5, mrrs5, recalls10, mrrs10 = [], [], [], []
    for case in cases:
        started = time.perf_counter()
        if mode == "dense":
            candidates = await dense.retrieve(
                RetrievalQuery(text=case.query, knowledge_base_id=case.knowledge_base_id),
                top_k=RETRIEVE_TOP_K,
            )
            results = candidates[:TOP_K]
        elif mode == "hybrid_rrf":
            candidates = await hybrid.retrieve(
                RetrievalQuery(text=case.query, knowledge_base_id=case.knowledge_base_id),
                top_k=RETRIEVE_TOP_K,
            )
            results = candidates[:TOP_K]
        else:  # hybrid_rerank
            results = await service.retrieve(
                query=case.query,
                knowledge_base_id=case.knowledge_base_id,
                retrieve_top_k=RETRIEVE_TOP_K,
                final_top_k=10,
                rerank=True,
            )
            candidates = results
        latencies.append((time.perf_counter() - started) * 1000)
        s5 = score_at_k(case, results, 5)
        s10 = score_at_k(case, candidates, 10)
        recalls5.append(s5["recall"])
        mrrs5.append(s5["rr"])
        recalls10.append(s10["recall"])
        mrrs10.append(s10["rr"])
        rows.append({
            "mode": mode,
            "query": case.query,
            "recall@5": round(s5["recall"], 4),
            "mrr@5": round(s5["rr"], 4),
            "recall@10": round(s10["recall"], 4),
            "mrr@10": round(s10["rr"], 4),
            "latency_ms": round(latencies[-1], 2),
            "top1": results[0].source if results else None,
            "top1_chunk": results[0].chunk_index if results else None,
        })
    agg = {
        "recall@5": round(statistics.mean(recalls5), 4),
        "mrr@5": round(statistics.mean(mrrs5), 4),
        "hits@5": sum(r["recall@5"] > 0 for r in rows),
        "queries": len(rows),
        "latency_mean_ms": round(statistics.mean(latencies), 2),
        "latency_p50_ms": round(pct(latencies, 50), 2),
        "latency_p95_ms": round(pct(latencies, 95), 2),
        "latency_p99_ms": round(pct(latencies, 99), 2),
        "recall@10": round(statistics.mean(recalls10), 4),
        "mrr@10": round(statistics.mean(mrrs10), 4),
    }
    return agg, rows


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--runs", type=int, default=2)
    parser.add_argument("--ollama", default="http://localhost:11434")
    parser.add_argument("--embedding-model", default="qwen3-embedding:0.6b")
    parser.add_argument(
        "--db",
        default="postgresql+asyncpg://rag:rag_password@localhost:5433/rag",
    )
    parser.add_argument(
        "--reranker-model",
        default=r"D:\Code\java_program\ai-prompt-benchmark\tools\models\bge-reranker-v2-m3",
    )
    parser.add_argument("--out-prefix", default="docs/benchmarks/raw/rag_idf")
    parser.add_argument("--modes", nargs="+", default=MODES, choices=MODES)
    args = parser.parse_args()

    cases = load_cases(Path(args.dataset))
    settings = get_settings()
    timeout = httpx.Timeout(
        timeout=settings.ollama_read_timeout, connect=settings.ollama_connect_timeout
    )
    engine = create_async_engine(args.db)
    try:
        async with httpx.AsyncClient(base_url=args.ollama, timeout=timeout) as http_client:
            ollama = OllamaClient(http_client=http_client, settings=settings)
            embedder = EmbeddingService(ollama, args.embedding_model)
            session_factory = create_session_factory(engine)
            dense = DenseRetriever(embedder, session_factory)
            keyword = KeywordRetriever(session_factory)
            hybrid = HybridRetriever(dense, keyword)
            reranker = BgeReranker(args.reranker_model)
            service = RetrievalService(hybrid, reranker)

            print("warmup...", flush=True)
            _ = await reranker.rerank(
                query="warmup",
                candidates=await hybrid.retrieve(
                    RetrievalQuery(text="warmup", knowledge_base_id=cases[0].knowledge_base_id),
                    top_k=5,
                ),
                top_k=2,
            )
            print("warmup done", flush=True)

            all_aggs: dict[str, list[dict]] = {}
            per_query_rows: dict[str, list[dict]] = {}
            for run in range(1, args.runs + 1):
                for mode in args.modes:
                    agg, rows = await run_once(cases, dense, hybrid, reranker, service, mode)
                    agg["run"] = run
                    all_aggs.setdefault(mode, []).append(agg)
                    per_query_rows.setdefault(mode, []).extend(rows)
                    print(json.dumps({"mode": mode, **agg}), flush=True)

            out_json = Path(f"{args.out_prefix}_matrix.json")
            out_json.parent.mkdir(parents=True, exist_ok=True)
            out_json.write_text(
                json.dumps(all_aggs, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            out_csv = Path(f"{args.out_prefix}_per_query.csv")
            with out_csv.open("w", newline="", encoding="utf-8-sig") as f:
                writer = csv.DictWriter(
                    f,
                    fieldnames=[
                        "mode", "query", "recall@5", "mrr@5", "recall@10", "mrr@10",
                        "latency_ms", "top1", "top1_chunk",
                    ],
                )
                writer.writeheader()
                for mode in args.modes:
                    writer.writerows(per_query_rows[mode])
            print(f"wrote {out_json} {out_csv}")
    finally:
        await engine.dispose()


if __name__ == "__main__":
    asyncio.run(main())
