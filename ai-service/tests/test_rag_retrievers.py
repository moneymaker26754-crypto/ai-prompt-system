from uuid import uuid4

import pytest

from app.rag.retrieval import dense_retriever as dense_module
from app.rag.retrieval.dense_retriever import DenseRetriever
from app.rag.retrieval.keyword_retriever import KeywordRetriever
from app.rag.models import RagChunk
from app.rag.retriever import RetrievalCandidate, RetrievalQuery
from app.rag.vector_store import SearchMode


class _SessionContext:
    def __init__(self, session, events):
        self.session = session
        self.events = events

    async def __aenter__(self):
        self.events.append("opened")
        return self.session

    async def __aexit__(self, *args):
        self.events.append("closed")


class _SessionFactory:
    def __init__(self, session):
        self.session = session
        self.events = []

    def __call__(self):
        return _SessionContext(self.session, self.events)


class _Embedder:
    async def embed_query(self, query):
        self.query = query
        return [0.25] * 1024


def _candidate() -> RetrievalCandidate:
    return RetrievalCandidate(
        chunk_id=uuid4(),
        document_id=uuid4(),
        content="dense result",
        source="docs/dense.md",
        file_name="dense.md",
        chunk_index=0,
        char_start=0,
        char_end=12,
        vector_score=0.75,
    )


@pytest.mark.anyio
async def test_dense_retriever_embeds_query_and_uses_scoped_vector_search(monkeypatch) -> None:
    result = _candidate()
    created_stores = []

    class _Store:
        def __init__(self, session):
            self.session = session
            created_stores.append(self)

        async def search(self, **kwargs):
            self.kwargs = kwargs
            return [result]

    monkeypatch.setattr(dense_module, "PgVectorStore", _Store)
    embedder = _Embedder()
    factory = _SessionFactory(object())
    request = RetrievalQuery(text="dense query", knowledge_base_id="kb-1")

    results = await DenseRetriever(embedder, factory).retrieve(request, top_k=7)

    assert results == [result]
    assert embedder.query == "dense query"
    assert created_stores[0].kwargs == {
        "embedding": [0.25] * 1024,
        "knowledge_base_id": "kb-1",
        "top_k": 7,
    }
    assert factory.events == ["opened", "closed"]


@pytest.mark.anyio
async def test_dense_retriever_owns_vector_benchmark_operations(monkeypatch) -> None:
    result = _candidate()

    class _Store:
        def __init__(self, session):
            self.session = session

        async def has_hnsw_index(self):
            return True

        async def benchmark_search(self, **kwargs):
            self.kwargs = kwargs
            return [result], 4.5

    monkeypatch.setattr(dense_module, "PgVectorStore", _Store)
    embedder = _Embedder()
    factory = _SessionFactory(object())
    dense = DenseRetriever(embedder, factory)

    embedding = await dense.embed_query("benchmark")
    has_index = await dense.has_hnsw_index()
    results, latency_ms = await dense.benchmark_by_embedding(
        embedding=embedding,
        knowledge_base_id="kb-2",
        top_k=10,
        mode=SearchMode.HNSW,
    )

    assert embedder.query == "benchmark"
    assert has_index is True
    assert results == [result]
    assert latency_ms == 4.5
    assert factory.events == ["opened", "closed", "opened", "closed"]


class _Rows:
    def __init__(self, rows):
        self.rows = rows

    def all(self):
        return self.rows


class _RecordingSession:
    def __init__(self, rows):
        self.rows = rows

    async def execute(self, statement):
        self.statement = statement
        return _Rows(self.rows)


@pytest.mark.anyio
async def test_keyword_retriever_uses_fts_and_maps_candidate_fields() -> None:
    chunk = RagChunk(
        id=uuid4(),
        document_id=uuid4(),
        knowledge_base_id="kb-1",
        chunk_index=3,
        content="keyword result",
        char_start=4,
        char_end=18,
        metadata_={"source": "docs/keyword.md", "file_name": "keyword.md"},
    )
    session = _RecordingSession([(chunk, 0.6)])
    factory = _SessionFactory(session)
    request = RetrievalQuery(text="keyword query", knowledge_base_id="kb-1")

    results = await KeywordRetriever(factory).retrieve(request, top_k=9)

    params = session.statement.compile().params
    # 关键词通道已切换为 OR 语义 tsquery：自然语言查询按词元拆分后以 | 连接
    assert "keyword | query" in params.values()
    assert "kb-1" in params.values()
    assert 9 in params.values()
    assert "@@" in str(session.statement)
    assert results == [
        RetrievalCandidate(
            chunk_id=chunk.id,
            document_id=chunk.document_id,
            content="keyword result",
            source="docs/keyword.md",
            file_name="keyword.md",
            chunk_index=3,
            char_start=4,
            char_end=18,
            keyword_score=0.6,
        )
    ]
    assert factory.events == ["opened", "closed"]
