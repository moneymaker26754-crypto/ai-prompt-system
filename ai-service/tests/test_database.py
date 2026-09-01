from app.database import create_async_engine, create_session_factory


def test_create_session_factory_keeps_orm_objects_available_after_commit() -> None:
    engine = create_async_engine("postgresql+asyncpg://rag:rag_password@localhost:5433/rag")

    session_factory = create_session_factory(engine)

    assert session_factory.kw["bind"] is engine
    assert session_factory.kw["expire_on_commit"] is False
