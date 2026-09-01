import pytest

from app.rag.schemas import RagEvaluateRequest, RagIngestRequest, RagSearchRequest


def test_ingest_request_includes_the_target_knowledge_base() -> None:
    request = RagIngestRequest(
        path="knowledge/intro.md",
        knowledge_base_id="  knowledge-base-1  ",
    )

    assert request.knowledge_base_id == "knowledge-base-1"


def test_ingest_request_rejects_blank_or_overlong_knowledge_base_id() -> None:
    with pytest.raises(ValueError):
        RagIngestRequest(path="knowledge/intro.md", knowledge_base_id="   ")
    with pytest.raises(ValueError):
        RagIngestRequest(path="knowledge/intro.md", knowledge_base_id="x" * 65)


def test_search_request_exposes_only_the_public_search_controls() -> None:
    request = RagSearchRequest(
        knowledge_base_id="  knowledge-base-1  ",
        query="  where do I start?  ",
    )

    assert request.knowledge_base_id == "knowledge-base-1"
    assert request.query == "where do I start?"
    assert request.top_k == 10
    assert request.rerank is False

    with pytest.raises(ValueError):
        RagSearchRequest(
            knowledge_base_id="knowledge-base-1",
            query="question",
            retrieve_top_k=20,
        )


def test_search_request_rejects_blank_text_and_out_of_range_top_k() -> None:
    with pytest.raises(ValueError):
        RagSearchRequest(knowledge_base_id="kb", query="  ")
    with pytest.raises(ValueError):
        RagSearchRequest(knowledge_base_id="kb", query="question", top_k=21)


def test_evaluate_request_has_safe_defaults_and_forbids_unknown_controls() -> None:
    request = RagEvaluateRequest(dataset="  rag_eval.jsonl  ")

    assert request.dataset == "rag_eval.jsonl"
    assert request.top_k == 5
    assert request.rerank is False
    assert request.include_failed_queries is False

    with pytest.raises(ValueError):
        RagEvaluateRequest(dataset="rag_eval.jsonl", top_k=21)
    with pytest.raises(ValueError):
        RagEvaluateRequest(dataset="rag_eval.jsonl", unexpected=True)
