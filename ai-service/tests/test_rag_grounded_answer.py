from uuid import uuid4

import pytest
from langchain_core.documents import Document

from app.rag.generation.citation_validator import CitationValidator
from app.rag.generation.context_builder import ContextBuilder
from app.rag.generation.grounded_answer_service import GroundedAnswerService
from app.rag.generation.langchian_retriever import ProjectRetriever
from app.rag.generation.schemas import GroundedAnswer, RagAnswerRequest
from app.rag.retriever import RetrievalCandidate


class FakeRetrievalService:
    def __init__(self, candidates):
        self.candidates = candidates
        self.kwargs = None

    async def retrieve(self, **kwargs):
        self.kwargs = kwargs
        return self.candidates


class FakeProjectRetriever:
    def __init__(self, documents):
        self.documents = documents
        self.question = None
        self.knowledge_base_id = None

    async def ainvoke(self, question, *, knowledge_base_id):
        self.question = question
        self.knowledge_base_id = knowledge_base_id
        return self.documents


class FakeAnswerGenerator:
    def __init__(self):
        self.context = None

    async def generate(self, question, context):
        self.context = context
        return GroundedAnswer(
            answerable=True,
            answer="Use the quickstart guide.",
            citations=["S1"],
        )


def make_candidate() -> RetrievalCandidate:
    return RetrievalCandidate(
        chunk_id=uuid4(),
        document_id=uuid4(),
        content="Read the quickstart guide.",
        source="docs/quickstart.md",
        file_name="quickstart.md",
        chunk_index=2,
        char_start=30,
        char_end=56,
        vector_score=0.85,
        keyword_score=0.40,
        fusion_score=0.03,
        rerank_score=0.93,
    )


def test_rag_answer_request_requires_and_normalizes_knowledge_base_id() -> None:
    request = RagAnswerRequest(
        knowledge_base_id="  kb-1  ",
        question="  How do I start?  ",
    )

    assert request.knowledge_base_id == "kb-1"
    assert request.question == "How do I start?"


@pytest.mark.anyio
async def test_project_retriever_forwards_request_scope_and_maps_metadata() -> None:
    candidate = make_candidate()
    retrieval_service = FakeRetrievalService([candidate])
    retriever = ProjectRetriever(
        retrieval_service=retrieval_service,
        retrieve_top_k=20,
        final_top_k=5,
        rerank=True,
    )

    documents = await retriever.ainvoke(
        "How do I start?",
        knowledge_base_id="kb-1",
    )

    assert retrieval_service.kwargs == {
        "query": "How do I start?",
        "knowledge_base_id": "kb-1",
        "retrieve_top_k": 20,
        "final_top_k": 5,
        "rerank": True,
    }
    assert documents[0].page_content == candidate.content
    assert documents[0].metadata["chunk_id"] == str(candidate.chunk_id)
    assert documents[0].metadata["document_id"] == str(candidate.document_id)
    assert documents[0].metadata["rerank_score"] == 0.93


def test_context_builder_creates_citable_source_blocks() -> None:
    document = Document(
        page_content="Read the quickstart guide.",
        metadata={
            "chunk_id": "chunk-1",
            "document_id": "document-1",
            "file_name": "quickstart.md",
            "chunk_index": 2,
        },
    )

    context = ContextBuilder(max_chars=1000).build([document])

    assert context.text == (
        "[S1]\nSource: quickstart.md\nChunk: 2\n\nRead the quickstart guide."
    )
    assert context.sources[0].source_id == "S1"
    assert context.sources[0].chunk_id == "chunk-1"
    assert context.sources[0].document_id == "document-1"


@pytest.mark.anyio
async def test_grounded_answer_uses_request_knowledge_base_and_maps_citations() -> None:
    document = Document(
        page_content="Read the quickstart guide.",
        metadata={
            "chunk_id": "chunk-1",
            "document_id": "document-1",
            "file_name": "quickstart.md",
            "chunk_index": 2,
            "rerank_score": 0.93,
        },
    )
    retriever = FakeProjectRetriever([document])
    generator = FakeAnswerGenerator()
    service = GroundedAnswerService(
        retriever=retriever,
        context_builder=ContextBuilder(max_chars=1000),
        generator=generator,
        citation_validator=CitationValidator(),
        min_rerank_score=0.50,
    )

    response = await service.answer(
        question="How do I start?",
        knowledge_base_id="kb-1",
    )

    assert retriever.question == "How do I start?"
    assert retriever.knowledge_base_id == "kb-1"
    assert generator.context == (
        "[S1]\nSource: quickstart.md\nChunk: 2\n\nRead the quickstart guide."
    )
    assert response.answerable is True
    assert response.answer == "Use the quickstart guide."
    assert response.citations[0].source_id == "S1"
    assert response.citations[0].chunk_id == "chunk-1"


def test_context_builder_counts_separators_in_the_character_budget() -> None:
    documents = [
        Document(page_content="first", metadata={"file_name": "a", "chunk_index": 0}),
        Document(page_content="second", metadata={"file_name": "b", "chunk_index": 1}),
    ]
    first_block = "[S1]\nSource: a\nChunk: 0\n\nfirst"
    second_block = "[S2]\nSource: b\nChunk: 1\n\nsecond"
    budget_without_separator = len(first_block) + len(second_block)

    context = ContextBuilder(max_chars=budget_without_separator).build(documents)

    assert context.text == first_block
    assert len(context.text) <= budget_without_separator
    assert [source.source_id for source in context.sources] == ["S1"]


def test_context_builder_does_not_register_a_source_when_its_header_cannot_fit() -> None:
    document = Document(
        page_content="content that is longer than the budget",
        metadata={"file_name": "a", "chunk_index": 0},
    )

    context = ContextBuilder(max_chars=20).build([document])

    assert context.text == ""
    assert context.sources == []
