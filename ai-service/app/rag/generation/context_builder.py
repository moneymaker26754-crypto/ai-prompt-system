from dataclasses import dataclass

from langchain_core.documents import Document


@dataclass(frozen=True)
class SourceReference:
    source_id: str
    chunk_id: str | None
    document_id: str | None
    file_name: str | None
    chunk_index: int | None
    content: str


@dataclass(frozen=True)
class BuiltContext:
    text: str
    sources: list[SourceReference]


class ContextBuilder:
    def __init__(self, max_chars: int = 12000):
        self.max_chars = max_chars

    def build(self, documents: list[Document]) -> BuiltContext:
        separator = "\n\n---\n\n"
        blocks: list[str] = []
        sources: list[SourceReference] = []
        used_chars = 0

        for index, document in enumerate(documents, start=1):
            source_id = f"S{index}"
            metadata = document.metadata
            file_name = metadata.get("file_name")
            chunk_index = metadata.get("chunk_index")
            header = (
                f"[{source_id}]\n"
                f"Source: {file_name}\n"
                f"Chunk: {chunk_index}\n\n"
            )
            content = document.page_content.strip()
            block = header + content
            separator_chars = len(separator) if blocks else 0
            remaining_chars = self.max_chars - used_chars - separator_chars
            if remaining_chars <= len(header):
                break

            if len(block) > remaining_chars:
                if blocks:
                    break
                block = header + content[: remaining_chars - len(header)]

            blocks.append(block)
            sources.append(
                SourceReference(
                    source_id=source_id,
                    chunk_id=metadata.get("chunk_id"),
                    document_id=metadata.get("document_id"),
                    file_name=file_name,
                    chunk_index=chunk_index,
                    content=document.page_content,
                )
            )
            used_chars += separator_chars + len(block)

        return BuiltContext(
            text=separator.join(blocks),
            sources=sources,
        )
