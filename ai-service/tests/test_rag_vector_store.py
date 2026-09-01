from uuid import uuid4

import pytest

from app.rag.models import RagChunk
from app.rag import vector_store as vector_store_module
from app.rag.vector_store import PgVectorStore, SearchMode


class _Result:
    def __init__(self, rows):
        self._rows = rows

    def all(self):
        return self._rows


class RecordingSession:
    def __init__(self, rows):
        self.rows = rows

    async def execute(self, statement):
        self.statement = statement
        return _Result(self.rows)


class RecordingStatementsSession:
    def __init__(self, rows):
        self.rows = rows
        self.statements = []

    async def execute(self, statement):
        self.statements.append(statement)
        return _Result(self.rows)


@pytest.mark.anyio
async def test_search_filters_by_knowledge_base_and_maps_chunk_fields():
    chunk = RagChunk(
        id=uuid4(),
        document_id=uuid4(),
        knowledge_base_id="kb-a",
        chunk_index=3,
        content="matching text",
        char_start=12,
        char_end=25,
        metadata_={"source": "docs/intro.md", "file_name": "intro.md"},
        embedding=[0.1] * 1024,
    )
    session = RecordingSession([(chunk, 0.25)])

    results = await PgVectorStore(session).search(
        embedding=[0.2] * 1024,
        knowledge_base_id="kb-a",
        top_k=7,
    )

    params = session.statement.compile().params
    assert "kb-a" in params.values()
    assert 7 in params.values()
    assert "rag_chunk.embedding IS NOT NULL" in str(session.statement)
    assert len(results) == 1
    result = results[0]
    assert result.chunk_id == chunk.id
    assert result.document_id == chunk.document_id
    assert result.source == "docs/intro.md"
    assert result.file_name == "intro.md"
    assert result.chunk_index == 3
    assert result.char_start == 12
    assert result.char_end == 25
    assert result.vector_score == 0.75
    assert result.rerank_score is None
    assert result.content == "matching text"


@pytest.mark.anyio
@pytest.mark.parametrize(
    ("mode", "expected_setting"),
    [
        (SearchMode.EXACT, "SET LOCAL enable_indexscan = off"),
        (SearchMode.HNSW, "SET LOCAL enable_seqscan = off"),
    ],
)
async def test_search_configures_the_requested_benchmark_mode(mode, expected_setting):
    session = RecordingStatementsSession([])

    await PgVectorStore(session).search(
        embedding=[0.2] * 1024,
        knowledge_base_id="kb-a",
        top_k=10,
        mode=mode,
    )

    statements = [str(statement) for statement in session.statements]
    assert statements[:2] == [
        "SET LOCAL enable_indexscan = on",
        "SET LOCAL enable_seqscan = on",
    ]
    assert expected_setting in statements
    if mode is SearchMode.HNSW:
        assert "SET LOCAL hnsw.ef_search = 40" in statements


@pytest.mark.anyio
async def test_planner_mode_does_not_change_postgres_session_settings():
    session = RecordingStatementsSession([])

    await PgVectorStore(session).search(
        embedding=[0.2] * 1024,
        knowledge_base_id="kb-a",
        top_k=10,
    )

    assert len(session.statements) == 1
    assert "SELECT" in str(session.statements[0])


class _ScalarResult:
    def __init__(self, value):
        self.value = value

    def scalar_one(self):
        return self.value


class IndexLookupSession:
    async def execute(self, statement):
        self.statement = statement
        return _ScalarResult(True)


@pytest.mark.anyio
async def test_has_hnsw_index_checks_the_expected_cosine_index():
    session = IndexLookupSession()

    exists = await PgVectorStore(session).has_hnsw_index()

    assert exists is True
    statement = str(session.statement)
    assert "rag_chunk_embedding_hnsw" in statement
    assert "vector_cosine_ops" in statement
    assert "m=16" in statement
    assert "ef_construction=64" in statement


class BenchmarkSession:
    def __init__(self, rows, events):
        self.rows = rows
        self.events = events

    async def execute(self, statement):
        self.events.append(str(statement))
        return _Result(self.rows)


@pytest.mark.anyio
async def test_benchmark_search_times_only_the_vector_select(monkeypatch):
    events = []
    session = BenchmarkSession([], events)
    times = iter([1_000_000, 6_000_000])

    def fake_clock():
        events.append("clock")
        return next(times)

    monkeypatch.setattr(vector_store_module, "perf_counter_ns", fake_clock)

    results, latency_ms = await PgVectorStore(session).benchmark_search(
        embedding=[0.2] * 1024,
        knowledge_base_id="kb-a",
        top_k=10,
        mode=SearchMode.HNSW,
    )

    assert results == []
    assert latency_ms == 5.0
    assert events[:4] == [
        "SET LOCAL enable_indexscan = on",
        "SET LOCAL enable_seqscan = on",
        "SET LOCAL enable_seqscan = off",
        "SET LOCAL hnsw.ef_search = 40",
    ]
    assert events[4] == "clock"
    assert "SELECT" in events[5]
    assert events[6] == "clock"
