import json
import logging
import os
from datetime import datetime, timezone

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from app.core.request_context import get_request_id


_STRUCTURED_FIELDS = (
    "requestId",
    "traceId",
    "operation",
    "model",
    "durationMs",
    "status",
    "promptLength",
    "optimizedPromptLength",
    "analysisLength",
    "errorCode",
    "errorType",
)


def get_trace_id() -> str | None:
    span_context = trace.get_current_span().get_span_context()
    if not span_context.is_valid:
        return None
    return format(span_context.trace_id, "032x")


class StructuredLogFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "event": record.getMessage(),
        }

        for field in _STRUCTURED_FIELDS:
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value

        payload.setdefault("requestId", get_request_id())
        payload.setdefault("traceId", get_trace_id())

        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)

        return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def configure_logging() -> None:
    app_logger = logging.getLogger("app")
    if any(
            getattr(handler, "_ai_structured_handler", False)
            for handler in app_logger.handlers
    ):
        return

    handler = logging.StreamHandler()
    handler.setFormatter(StructuredLogFormatter())
    handler._ai_structured_handler = True
    app_logger.addHandler(handler)
    app_logger.setLevel(logging.INFO)
    app_logger.propagate = False


def setup_telemetry(app, otlp_endpoint: str = None):
    """Setup OpenTelemetry with OTLP exporter and instrumentations"""

    if otlp_endpoint is None:
        otlp_endpoint = os.getenv("AI_OTEL_ENDPOINT", "http://localhost:4317")

    # Configure OTLP exporter
    otlp_exporter = OTLPSpanExporter(endpoint=otlp_endpoint, insecure=True)

    # Configure TracerProvider
    tracer_provider = TracerProvider(
        resource=Resource.create({"service.name": "ai-prompt-service"})
    )
    span_processor = BatchSpanProcessor(otlp_exporter)
    tracer_provider.add_span_processor(span_processor)

    # Set global tracer provider
    trace.set_tracer_provider(tracer_provider)

    # Instrument FastAPI
    FastAPIInstrumentor.instrument_app(app)

    # Instrument HTTPX
    HTTPXClientInstrumentor().instrument()
