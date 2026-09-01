import hashlib

from dataclasses import dataclass
from pathlib import Path


SUPPORTED_EXTENSIONS = {".txt", ".md"}
CONTENT_TYPES = {
    ".md": "text/markdown",
    ".txt": "text/plain",
}


@dataclass(frozen=True)
class LoadedDocument:
    source_path: str
    file_name: str
    content_type: str
    text: str
    content_hash: str
    file_size: int


class UnsupportedDocumentError(ValueError):
    pass


def load_document(path: Path) -> LoadedDocument:
    if path.suffix.lower() not in SUPPORTED_EXTENSIONS:
        raise UnsupportedDocumentError(f"Unsupported document type: {path.suffix}")

    text = path.read_text(encoding="utf-8", errors="strict",)

    text = normalize_text(text)

    if not text.strip():
        raise ValueError("document is empty")

    source_path = path.resolve().relative_to(Path.cwd().resolve()).as_posix()

    return LoadedDocument(
        source_path=source_path,
        file_name=path.name,
        content_type=CONTENT_TYPES[path.suffix.lower()],
        text=text,
        content_hash=sha256_text(text),
        file_size=path.stat().st_size,
    )



def normalize_text(text: str) -> str:
    text = text.replace("\r\n", "\n")
    text = text.replace("\r", "\n")

    return text.strip()


def sha256_text(text: str) -> str:
    normalized = normalize_text(text).encode("utf-8")
    return hashlib.sha256(normalized).hexdigest()
