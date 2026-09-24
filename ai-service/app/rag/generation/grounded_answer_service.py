from app.rag.generation.answer_generator import LangChainAnswerGenerator
from app.rag.generation.citation_validator import CitationValidator, CitationValidationError
from app.rag.generation.context_builder import ContextBuilder, BuiltContext
from app.rag.generation.langchain_retriever import ProjectRetriever
from app.rag.generation.schemas import RagAnswerResponse, CitationResponse


class GroundedAnswerService:

    def __init__(
            self,
            retriever: ProjectRetriever,

            context_builder: ContextBuilder,

            generator:
                LangChainAnswerGenerator,

            citation_validator:
                CitationValidator,

            min_rerank_score:
                float | None = None,
    ):
        self.retriever = retriever

        self.context_builder = (
            context_builder
        )

        self.generator = generator

        self.citation_validator = (
            citation_validator
        )

        self.min_rerank_score = (
            min_rerank_score
        )


    async def answer(
            self,
            question: str,
            knowledge_base_id: str,
    ) -> RagAnswerResponse:
        documents = (
            await self.retriever.ainvoke(
                question,
                knowledge_base_id=knowledge_base_id,
            )
        )

        if not documents:

            return RagAnswerResponse(
                answerable=False,

                answer=(
                    "当前知识库中没有找到"
                    "能够回答该问题的信息。"
                ),

                citations=[],

                reason="NO_RETRIEVAL_RESULTS",
            )

        if not self._passes_score_gate(
                documents
        ):
            return RagAnswerResponse(
                answerable=False,

                answer=(
                    "当前检索结果不足以可靠"
                    "回答该问题。"
                ),

                citations=[],

                reason=(
                    "LOW_RETRIEVAL_CONFIDENCE"
                ),
            )

        context = (
            self.context_builder.build(
                documents
            )
        )

        generated = (
            await self.generator.generate(
                question,
                context.text,
            )
        )

        if not generated.answerable:

            return RagAnswerResponse(
                answerable=False,

                answer=generated.answer,

                citations=[],

                reason=(
                        generated.reason
                        or "INSUFFICIENT_CONTEXT"
                ),
            )

        try:

            validated = (
                self.citation_validator
                .validate(
                    generated,
                    context,
                )
            )

        except CitationValidationError:

            return RagAnswerResponse(
                answerable=False,

                answer=(
                    "生成结果未能通过"
                    "引用一致性验证。"
                ),

                citations=[],

                reason=(
                    "GROUNDING_VALIDATION_FAILED"
                ),
            )

        citations = (
            self._map_citations(
                validated.citations,
                context,
            )
        )

        return RagAnswerResponse(
            answerable=True,

            answer=validated.answer,

            citations=citations,

            reason=None,
        )

    async def aclose(self) -> None:
        await self.generator.aclose()


    def _passes_score_gate(
            self,
            documents,
    ) -> bool:

        if self.min_rerank_score is None:
            return True

        best = (
            documents[0]
            .metadata
            .get("rerank_score")
        )

        if best is None:
            return True

        return (
            best >= self.min_rerank_score
        )


    @staticmethod
    def _map_citations(
            citation_ids: list[str],
            context: BuiltContext,
    ) -> list[CitationResponse]:

        source_map = {
            source.source_id: source for source in context.sources
        }

        result = []

        for citation_id in citation_ids:

            source = source_map[citation_id]

            result.append(
                CitationResponse(
                    source_id=source.source_id,
                    document_id=source.document_id,
                    chunk_id=source.chunk_id,
                    file_name=source.file_name,
                    chunk_index=source.chunk_index,
                    preview=source.content[:300],
                )
            )

        return result
