from sqlalchemy import UniqueConstraint

from app.rag.models import RagChunk, RagDocument


def test_rag_models_map_the_existing_singular_schema() -> None:
    assert RagDocument.__tablename__ == "rag_document"
    assert set(RagDocument.__table__.columns.keys()) == {
        "id",
        "knowledge_base_id",
        "file_name",
        "content_type",
        "content_hash",
        "created_at",
    }
    assert RagChunk.__tablename__ == "rag_chunk"
    assert set(RagChunk.__table__.columns.keys()) == {
        "id",
        "document_id",
        "knowledge_base_id",
        "chunk_index",
        "content",
        "char_start",
        "char_end",
        "token_count",
        "metadata",
        "embedding",
        "created_at",
    }

    document_constraints = [
        constraint
        for constraint in RagDocument.__table__.constraints
        if isinstance(constraint, UniqueConstraint)
    ]
    assert any(
        {column.name for column in constraint.columns}
        == {"knowledge_base_id", "content_hash"}
        for constraint in document_constraints
    )
    foreign_key = next(iter(RagChunk.__table__.c.document_id.foreign_keys))
    assert foreign_key.target_fullname == "rag_document.id"
    assert foreign_key.ondelete == "CASCADE"
    assert RagDocument.__table__.c.knowledge_base_id.type.length == 64
    assert RagChunk.__table__.c.knowledge_base_id.type.length == 64
    assert RagDocument.__table__.c.content_type.type.length == 100
    assert RagDocument.__table__.c.content_type.nullable is True
    assert RagChunk.__table__.c.char_start.nullable is True
    assert RagChunk.__table__.c.char_end.nullable is True
    assert RagChunk.__table__.c.token_count.nullable is True
    assert RagChunk.__table__.c.embedding.nullable is True
    assert RagChunk.__table__.c.metadata.server_default.arg.text == "'{}'::jsonb"
    assert not any(
        isinstance(constraint, UniqueConstraint)
        for constraint in RagChunk.__table__.constraints
    )
    assert str(RagChunk.__table__.c.embedding.type) == "VECTOR(1024)"
