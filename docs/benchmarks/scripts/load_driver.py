"""Async HTTP load driver: TPS + latency percentiles for RAG and Java endpoints.

Usage examples:
  python scripts/load_driver.py --endpoint rag-search --users 20 --duration 60 \
      --url http://127.0.0.1:8000 --api-key bench-key --queries rag-eval/datasets/p1.jsonl --rerank both
  python scripts/load_driver.py --endpoint java-view --users 50 --duration 60 \
      --url http://127.0.0.1:8080 --prompt-ids 1..200
  python scripts/load_driver.py --endpoint java-like --users 50 --duration 60 \
      --url http://127.0.0.1:8080 --bearer-file tokens.json --prompt-ids 1..200
"""
import argparse
import asyncio
import json
import random
import statistics
import time
from pathlib import Path

import httpx


def pct(values, q):
    ordered = sorted(values)
    idx = min(len(ordered) - 1, max(0, round(q / 100 * (len(ordered) - 1))))
    return ordered[idx]


async def worker(client, args, user_id, latencies, errors, stop_at):
    rng = random.Random(user_id * 7919 + 17)
    queries = args.queries or ["What is retrieval augmented generation?"]
    prompt_ids = args.prompt_ids or [1]
    tokens = args.tokens or []
    while time.perf_counter() < stop_at:
        try:
            started = time.perf_counter()
            if args.endpoint == "rag-search":
                q = rng.choice(queries)
                body = {"knowledge_base_id": args.kb, "query": q, "top_k": 5, "rerank": args.rerank}
                headers = {"X-Internal-API-Key": args.api_key or ""}
                r = await client.post(f"{args.url}/internal/rag/search", json=body, headers=headers)
            elif args.endpoint == "rag-answer":
                q = rng.choice(queries)
                body = {"knowledge_base_id": args.kb, "question": q}
                headers = {"X-Internal-API-Key": args.api_key or ""}
                r = await client.post(f"{args.url}/internal/rag/answer", json=body, headers=headers)
            elif args.endpoint == "java-view":
                r = await client.get(f"{args.url}/api/prompts/{rng.choice(prompt_ids)}")
            elif args.endpoint in ("java-like", "java-copy", "java-favorite"):
                headers = {"Authorization": f"Bearer {rng.choice(tokens)}"} if tokens else {}
                path = {"java-like": "like", "java-copy": "copy", "java-favorite": "favorite"}[args.endpoint]
                r = await client.post(f"{args.url}/api/prompts/{rng.choice(prompt_ids)}/{path}", headers=headers)
            else:
                raise ValueError(f"unknown endpoint {args.endpoint}")
            latencies.append((time.perf_counter() - started) * 1000)
            if r.status_code >= 400:
                errors.append((r.status_code, r.text[:120]))
        except Exception as exc:
            errors.append(("EXC", str(exc)[:120]))


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--users", type=int, required=True)
    parser.add_argument("--duration", type=int, default=60)
    parser.add_argument("--url", required=True)
    parser.add_argument("--api-key", default=None)
    parser.add_argument("--kb", default="kb-main")
    parser.add_argument("--rerank", type=lambda s: s == "true", default=True)
    parser.add_argument("--queries-file", default=None)
    parser.add_argument("--prompt-ids", nargs="+", type=int, default=None)
    parser.add_argument("--bearer-file", default=None)
    parser.add_argument("--out", default="results/load.json")
    args = parser.parse_args()

    queries = None
    if args.queries_file:
        with Path(args.queries_file).open(encoding="utf-8") as f:
            queries = [json.loads(line)["query"] for line in f if line.strip()]
    tokens = None
    if args.bearer_file:
        tokens = json.loads(Path(args.bearer_file).read_text(encoding="utf-8"))
    args.queries = queries
    args.tokens = tokens

    latencies: list[float] = []
    errors: list[tuple] = []
    stop_at = time.perf_counter() + args.duration
    limits = httpx.Limits(max_connections=args.users * 2, max_keepalive_connections=args.users)
    async with httpx.AsyncClient(timeout=60, limits=limits) as client:
        tasks = [asyncio.create_task(worker(client, args, i, latencies, errors, stop_at)) for i in range(args.users)]
        await asyncio.gather(*tasks)

    total = len(latencies) + len(errors)
    result = {
        "endpoint": args.endpoint,
        "users": args.users,
        "duration_s": args.duration,
        "total_requests": total,
        "tps": round(total / args.duration, 2),
        "ok": len(latencies),
        "errors": len(errors),
        "latency_mean_ms": round(statistics.mean(latencies), 2) if latencies else None,
        "latency_p50_ms": round(pct(latencies, 50), 2) if latencies else None,
        "latency_p95_ms": round(pct(latencies, 95), 2) if latencies else None,
        "latency_p99_ms": round(pct(latencies, 99), 2) if latencies else None,
    }
    if errors:
        from collections import Counter
        result["error_sample"] = Counter(f"{c}: {m}" for c, m in errors).most_common(5)
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    asyncio.run(main())
