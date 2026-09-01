from app.rag.chunker import RecursiveChunker
from app.rag.document_loader import LoadedDocument


def test_short_document_produces_one_chunk() -> None:
    document = LoadedDocument(
        source_path="knowledge/short.md",
        file_name="short.md",
        content_type="text/markdown",
        text="short text",
        content_hash="hash",
        file_size=10,
    )

    chunks = RecursiveChunker(chunk_size=20, overlap=2).splits(document)

    assert [(chunk.content, chunk.char_start, chunk.char_end) for chunk in chunks] == [
        ("short text", 0, 10),
    ]


def test_multi_piece_document_keeps_each_overlapping_chunk_once_in_source_order() -> None:
    document = LoadedDocument(
        source_path="knowledge/segments.md",
        file_name="segments.md",
        content_type="text/markdown",
        text="one two three four",
        content_hash="hash",
        file_size=18,
    )

    chunks = RecursiveChunker(chunk_size=8, overlap=2).splits(document)

    assert [(chunk.content, chunk.char_start, chunk.char_end) for chunk in chunks] == [
        ("one two ", 0, 8),
        ("o three ", 6, 14),
        ("e four", 12, 18),
    ]


def test_chunks_have_zero_based_indexes_exclusive_ranges_and_source_metadata() -> None:
    document = LoadedDocument(
        source_path="knowledge/intro.md",
        file_name="intro.md",
        content_type="text/markdown",
        text="abcdefghij",
        content_hash="hash",
        file_size=10,
    )

    chunks = RecursiveChunker(chunk_size=5, overlap=2).splits(document)

    assert [(chunk.chunk_index, chunk.char_start, chunk.char_end) for chunk in chunks] == [
        (0, 0, 5),
        (1, 3, 8),
        (2, 6, 10),
    ]
    assert [chunk.content for chunk in chunks] == ["abcde", "defgh", "ghij"]
    assert chunks[1].metadata == {
        "source": "knowledge/intro.md",
        "file_name": "intro.md",
        "chunk_index": 1,
        "char_start": 3,
        "char_end": 8,
    }
