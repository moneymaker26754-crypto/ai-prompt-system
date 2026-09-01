from dataclasses import dataclass
from typing import TypedDict

from app.rag.document_loader import LoadedDocument


class ChunkMetadata(TypedDict):
    source: str
    file_name: str
    chunk_index: int
    char_start: int
    char_end: int



@dataclass(frozen=True)
class Chunk:
    chunk_index: int
    content: str
    char_start: int
    char_end: int
    source: str
    file_name: str

    @property
    def metadata(self) -> ChunkMetadata:
        return {
            "source": self.source,
            "file_name": self.file_name,
            "chunk_index": self.chunk_index,
            "char_start": self.char_start,
            "char_end": self.char_end,
        }


class RecursiveChunker:

    def __init__(
            self,
            chunk_size: int = 800,
            overlap: int = 100,
    ):
        if chunk_size <= 0:
            raise ValueError("chunk_size must be > 0")

        if overlap < 0:
            raise ValueError("overlap must be >= 0")

        if overlap >= chunk_size:
            raise ValueError("overlap must be < chunk_size")

        self.chunk_size = chunk_size
        self.overlap = overlap

        self.separators = [
            "\n# ",
            "\n## ",
            "\n### ",
            "\n\n",
            "\n",
            "。",
            "！",
            "？",
            ". ",
            " ",
        ]


    def splits(self, document: LoadedDocument) -> list[Chunk]:
        pieces = self._recursive_split(document.text, self.separators)

        merged = self._merge(pieces)

        return self._to_chunks(document, merged)


    def _recursive_split(self, text: str, separators: list[str]) -> list[str]:

        if len(text) <= self.chunk_size:
            return [text]

        if not separators:
            return [
                text[i:i + self.chunk_size]
                for i in range(
                    0,
                    len(text),
                    self.chunk_size
                )
            ]

        separator = separators[0]

        parts = text.split(separator)

        if len(parts) == 1:
            return self._recursive_split(text, separators[1:])

        result: list[str] = []

        for index, part in enumerate(parts):
            if not part:
                continue

            if index < len(parts) - 1:
                part += separator

            if len(part) > self.chunk_size:
                result.extend(
                    self._recursive_split(part, separators[1:])
                )
            else:
                result.append(part)

        return result


    def _merge(self, pieces: list[str]) -> list[str]:
        chunks: list[str] = []
        current = ""

        for piece in pieces:
            if len(current) + len(piece) <= self.chunk_size:
                current += piece
                continue

            if current:
                chunks.append(current)

            overlap_text = (
                current[-self.overlap:]
                if self.overlap
                else ""
            )
            current = overlap_text + piece

            while len(current) > self.chunk_size:
                chunks.append(current[:self.chunk_size])
                current = current[self.chunk_size - self.overlap:]

        if current:
            chunks.append(current)
        return chunks


    def _to_chunks(
            self,
            document: LoadedDocument,
            contents: list[str],
    ) -> list[Chunk]:

        chunks: list[Chunk] = []

        search_from = 0

        for chunk_index, content in enumerate(contents):
            search_from = max(0, search_from - self.overlap,)

            char_start = document.text.find(content, search_from)

            if char_start == -1:
                raise ValueError("Content not found in document")

            char_end = char_start + len(content)

            chunks.append(
                Chunk(
                    chunk_index=chunk_index,
                    content=content,
                    char_start=char_start,
                    char_end=char_end,
                    source=document.source_path,
                    file_name=document.file_name,
                )
            )

            search_from = char_end

        return chunks
