class AiServiceError(Exception):
    code="AI_SERVICE_ERROR"

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


class ModelTimeoutError(AiServiceError):
    code = "AI_TIMEOUT"


class ModelUnavailableError(AiServiceError):
    code = "AI_UNAVAILABLE"


class ModelUpstreamError(AiServiceError):
    code = "AI_UPSTREAM_ERROR"


class InvalidModelOutputError(AiServiceError):
    code = "INVALID_MODEL_OUTPUT"