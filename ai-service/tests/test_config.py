from app.core.config import Settings


def test_environment_can_be_overridden(monkeypatch):
    monkeypatch.setenv(
        "AI_ENVIRONMENT",
        "test",
    )

    settings = Settings()

    assert settings.environment == "test"