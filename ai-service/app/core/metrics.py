from prometheus_client import Counter, Histogram

from app.core.exceptions import (
    AiServiceError,
    InvalidModelOutputError,
    ModelTimeoutError,
    ModelUnavailableError,
)


AI_REQUEST_TOTAL = Counter(
    "ai_request_total",
    "AI operations",
    ["operation", "status"]
)

AI_REQUEST_DURATION = Histogram(
    "ai_request_duration_seconds",
    "AI request duration",
    ["operation"],
)

AI_INPUT_TOKENS = Counter(
    "ai_input_tokens_total",
    "Input tokens",
    ["operation"],
)

AI_OUTPUT_TOKENS = Counter(
    "ai_output_tokens_total",
    "Output tokens",
    ["operation"],
)


def status_for_exception(exc: AiServiceError) -> str:
    if isinstance(exc, ModelTimeoutError):
        return "timeout"
    if isinstance(exc, ModelUnavailableError):
        return "unavailable"
    if isinstance(exc, InvalidModelOutputError):
        return "invalid_output"
    return "upstream_error"


def record_ai_request(
        *,
        operation: str,
        status: str,
        duration_seconds: float,
        input_tokens: int | None = None,
        output_tokens: int | None = None,
) -> None:
    AI_REQUEST_TOTAL.labels(operation=operation, status=status).inc()
    AI_REQUEST_DURATION.labels(operation=operation).observe(duration_seconds)

    if input_tokens is not None:
        AI_INPUT_TOKENS.labels(operation=operation).inc(input_tokens)
    if output_tokens is not None:
        AI_OUTPUT_TOKENS.labels(operation=operation).inc(output_tokens)
