package com.jojo.prompt.integration.ai;

import com.jojo.prompt.common.exception.BusinessException;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

final class AiErrorMapping {

    private AiErrorMapping() {
    }

    static BusinessException fromCode(String errorCode) {
        return switch (errorCode == null ? "" : errorCode) {
            case "AI_TIMEOUT" -> new BusinessException(504, "AI_TIMEOUT");
            case "AI_UNAVAILABLE" -> new BusinessException(503, "AI_UNAVAILABLE");
            case "INVALID_MODEL_OUTPUT" -> new BusinessException(502, "INVALID_MODEL_OUTPUT");
            case "AI_UPSTREAM_ERROR" -> new BusinessException(502, "AI_UPSTREAM_ERROR");
            default -> new BusinessException(502, "AI_UPSTREAM_ERROR");
        };
    }

    static BusinessException fromTransport(Throwable throwable) {
        if (hasTimeoutCause(throwable)) {
            return fromCode("AI_TIMEOUT");
        }
        return fromCode("AI_UNAVAILABLE");
    }

    static BusinessException fromStreamFailure(Throwable throwable) {
        if (hasTimeoutCause(throwable)) {
            return fromCode("AI_TIMEOUT");
        }
        if (throwable instanceof WebClientRequestException) {
            return fromCode("AI_UNAVAILABLE");
        }
        return fromCode("INVALID_MODEL_OUTPUT");
    }

    private static boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
