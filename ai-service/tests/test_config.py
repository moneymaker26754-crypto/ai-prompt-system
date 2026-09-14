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


def test_grounded_answer_settings_use_the_documented_environment_variables(monkeypatch):
    monkeypatch.setenv("OLLAMA_BASE_URL", "http://ollama.internal:11434")
    monkeypatch.setenv("RAG_CHAT_MODEL", "qwen3:14b")
    monkeypatch.setenv("RAG_RETRIEVE_TOP_K", "30")
    monkeypatch.setenv("RAG_FINAL_TOP_K", "7")
    monkeypatch.setenv("RAG_MAX_CONTEXT_CHARS", "16000")
    monkeypatch.setenv("RAG_MIN_RERANK_SCORE", "0.65")

    settings = Settings(_env_file=None)

    assert settings.ollama_base_url == "http://ollama.internal:11434"
    assert settings.rag_chat_model == "qwen3:14b"
    assert settings.rag_retrieve_top_k == 30
    assert settings.rag_final_top_k == 7
    assert settings.rag_max_context_chars == 16000
    assert settings.rag_min_rerank_score == 0.65
