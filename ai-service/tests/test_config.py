from app.core.config import Settings


def test_environment_can_be_overridden(monkeypatch):
    monkeypatch.setenv(
        "AI_ENVIRONMENT",
        "test",
    )

    settings = Settings()

    assert settings.environment == "test"


def test_internal_rag_key_uses_the_ai_internal_api_key_environment_variable(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_API_KEY", "internal-secret")

    settings = Settings()

    assert settings.internal_api_key == "internal-secret"


def test_rag_evaluation_directory_uses_the_rag_environment_variable(monkeypatch):
    monkeypatch.setenv("RAG_EVAL_DATASET_DIR", "evaluation-data")

    settings = Settings()

    assert settings.rag_eval_dataset_dir.as_posix() == "evaluation-data"
