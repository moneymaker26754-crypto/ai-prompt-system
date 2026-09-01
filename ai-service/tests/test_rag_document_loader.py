from pathlib import Path

from app.rag.document_loader import load_document


def test_load_document_uses_relative_posix_source_path(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source_file = tmp_path / "knowledge" / "notes" / "intro.md"
    source_file.parent.mkdir(parents=True)
    source_file.write_text("Hello RAG", encoding="utf-8")
    monkeypatch.chdir(tmp_path)

    document = load_document(source_file)

    assert document.source_path == "knowledge/notes/intro.md"
    assert document.file_name == "intro.md"
