from contextlib import AsyncExitStack

from app.core.config import Settings
from app.rag.generation.answer_generator import LangChainAnswerGenerator
from app.rag.generation.citation_validator import CitationValidator
from app.rag.generation.context_builder import ContextBuilder
from app.rag.generation.grounded_answer_service import GroundedAnswerService
from app.rag.generation.langchain_retriever import ProjectRetriever
from app.rag.generation.model_factory import create_chat_model
from app.rag.retrieval.retrieval_service import RetrievalService


def build_grounded_answer_service(
    retrieval_service: RetrievalService,
    settings: Settings,
    exit_stack: AsyncExitStack | None = None,
) -> GroundedAnswerService:
    retriever = ProjectRetriever(
        retrieval_service=retrieval_service,
        retrieve_top_k=settings.rag_retrieve_top_k,
        final_top_k=settings.rag_final_top_k,
        rerank=True,
    )
    llm = create_chat_model(
        model_name=settings.rag_chat_model,
        base_url=settings.ollama_base_url,
    )
    if exit_stack is not None:
        exit_stack.callback(llm._client.close)
        exit_stack.push_async_callback(llm._async_client.close)

    return GroundedAnswerService(
        retriever=retriever,
        context_builder=ContextBuilder(max_chars=settings.rag_max_context_chars),
        generator=LangChainAnswerGenerator(llm=llm),
        citation_validator=CitationValidator(),
        min_rerank_score=settings.rag_min_rerank_score,
    )
