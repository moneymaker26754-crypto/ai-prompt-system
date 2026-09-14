from typing import Any

from langchain_core.documents import Document
from langchain_core.retrievers import BaseRetriever
from pydantic import ConfigDict


class ProjectRetriever(BaseRetriever):

    model_config = ConfigDict(
        arbitrary_types_allowed=True,
    )

    retrieval_service: Any

    retrieve_top_k: int = 20
    final_top_k: int = 5
    rerank: bool = True

    def _get_relevant_documents(
        self,
        query: str,
        *,
        knowledge_base_id: str,
        run_manager,
    ) -> list[Document]:
        raise RuntimeError(
            "ProjectRetriever is async-only. "
            "Use ainvoke()."
        )

    async def _aget_relevant_documents(
        self,
        query: str,
        *,
        knowledge_base_id: str,
        run_manager,
    ) -> list[Document]:
        candidates = (
            await self.retrieval_service.retrieve(
                query=query,
                knowledge_base_id=knowledge_base_id,
                retrieve_top_k=self.retrieve_top_k,
                final_top_k=self.final_top_k,
                rerank=self.rerank,
            )
        )

        return [
            self._to_document(candidate) for candidate in candidates
        ]


    @staticmethod
    def _to_document(candidate) -> Document:
        metadata = {
            "source": candidate.source,
            "file_name": candidate.file_name,
            "chunk_index": candidate.chunk_index,
            "char_start": candidate.char_start,
            "char_end": candidate.char_end,
            "chunk_id": str(candidate.chunk_id),
            "document_id": str(candidate.document_id),
            "vector_score": candidate.vector_score,
            "keyword_score": candidate.keyword_score,
            "fusion_score": candidate.fusion_score,
            "rerank_score": candidate.rerank_score,
        }

        return Document(
            page_content=candidate.content,
            metadata=metadata,
        )
