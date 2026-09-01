import json
import logging

from app.core import telemetry


def test_setup_telemetry_instruments_httpx_client_instance(monkeypatch):
    calls = []

    class FakeExporter:
        def __init__(self, **kwargs):
            calls.append(("exporter", kwargs))

    class FakeSpanProcessor:
        def __init__(self, exporter):
            calls.append(("processor", exporter.__class__.__name__))

    class FakeTracerProvider:
        def __init__(self, resource=None):
            calls.append(("service.name", resource.attributes.get("service.name") if resource else None))

        def add_span_processor(self, processor):
            calls.append(("provider", processor.__class__.__name__))

    class FakeFastApiInstrumentor:
        @staticmethod
        def instrument_app(app):
            calls.append(("fastapi", app))

    class FakeHttpxInstrumentor:
        def instrument(self):
            calls.append(("httpx", "instrumented"))

    monkeypatch.setattr(telemetry, "OTLPSpanExporter", FakeExporter)
    monkeypatch.setattr(telemetry, "BatchSpanProcessor", FakeSpanProcessor)
    monkeypatch.setattr(telemetry, "TracerProvider", FakeTracerProvider)
    monkeypatch.setattr(telemetry, "FastAPIInstrumentor", FakeFastApiInstrumentor)
    monkeypatch.setattr(telemetry, "HTTPXClientInstrumentor", FakeHttpxInstrumentor)
    monkeypatch.setattr(telemetry.trace, "set_tracer_provider", lambda provider: None)

    app = object()
    telemetry.setup_telemetry(app, "http://collector:4317")

    assert ("httpx", "instrumented") in calls
    assert ("fastapi", app) in calls
    assert ("service.name", "ai-prompt-service") in calls


def test_structured_log_formatter_includes_context_and_excludes_sensitive_fields(monkeypatch):
    monkeypatch.setattr(telemetry, "get_request_id", lambda: "request-123")
    monkeypatch.setattr(telemetry, "get_trace_id", lambda: "a" * 32)
    record = logging.LogRecord(
        name="app.services.analyze",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="ai operation completed",
        args=(),
        exc_info=None,
    )
    record.operation = "analyze"
    record.status = "success"
    record.durationMs = 25
    record.prompt = "secret prompt"
    record.jwt = "secret token"

    payload = json.loads(telemetry.StructuredLogFormatter().format(record))

    assert payload["event"] == "ai operation completed"
    assert payload["requestId"] == "request-123"
    assert payload["traceId"] == "a" * 32
    assert payload["operation"] == "analyze"
    assert payload["durationMs"] == 25
    assert "secret prompt" not in json.dumps(payload)
    assert "secret token" not in json.dumps(payload)


def test_configure_logging_is_idempotent():
    logger = logging.getLogger("app")
    previous_handlers = list(logger.handlers)
    previous_propagate = logger.propagate
    previous_level = logger.level
    try:
        logger.handlers = []
        telemetry.configure_logging()
        telemetry.configure_logging()

        structured_handlers = [
            handler
            for handler in logger.handlers
            if getattr(handler, "_ai_structured_handler", False)
        ]
        assert len(structured_handlers) == 1
    finally:
        logger.handlers = previous_handlers
        logger.propagate = previous_propagate
        logger.setLevel(previous_level)
