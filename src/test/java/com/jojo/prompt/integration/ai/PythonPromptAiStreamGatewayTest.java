package com.jojo.prompt.integration.ai;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.filter.RequestIdFilter;
import com.jojo.prompt.dto.request.PromptOptimizeRequestDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonPromptAiStreamGatewayTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void optimizeStreamShouldForwardRequestId() {
        MDC.put(RequestIdFilter.MDC_KEY, "observability-test-1");
        ExchangeFunction exchange = request -> {
            assertEquals(
                    "observability-test-1",
                    request.headers().getFirst(RequestIdFilter.HEADER)
            );
            return Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_NDJSON_VALUE)
                            .body("{\"type\":\"done\",\"model\":\"qwen3.5:9b\"}\n")
                            .build()
            );
        };
        PythonPromptAiStreamGateway gateway = gateway(exchange);

        List<PromptAiStreamChunk> chunks = gateway.optimizeStream(request(), null, "analysis")
                .collectList()
                .block();

        assertEquals("done", chunks.getFirst().type());
    }

    @Test
    void optimizeStreamShouldMapRemoteUnavailableError() {
        ExchangeFunction exchange = request -> Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"code\":\"AI_UNAVAILABLE\",\"message\":\"private detail\"}")
                        .build()
        );
        PythonPromptAiStreamGateway gateway = gateway(exchange);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.optimizeStream(request(), null, "analysis").collectList().block()
        );

        assertEquals(503, exception.getCode());
        assertEquals("AI_UNAVAILABLE", exception.getMessage());
    }

    @Test
    void optimizeStreamShouldMapTimeoutWithoutRetrying() {
        int[] calls = {0};
        ExchangeFunction exchange = request -> {
            calls[0]++;
            return Mono.error(new TimeoutException("private timeout detail"));
        };
        PythonPromptAiStreamGateway gateway = gateway(exchange);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.optimizeStream(request(), null, "analysis").collectList().block()
        );

        assertEquals(504, exception.getCode());
        assertEquals("AI_TIMEOUT", exception.getMessage());
        assertEquals(1, calls[0]);
    }

    @Test
    void optimizeStreamShouldClassifyMalformedChunkAsInvalidOutput() {
        ExchangeFunction exchange = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_NDJSON_VALUE)
                        .body("not json at all\n")
                        .build()
        );
        PythonPromptAiStreamGateway gateway = gateway(exchange);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.optimizeStream(request(), null, "analysis").collectList().block()
        );

        assertEquals(502, exception.getCode());
        assertEquals("INVALID_MODEL_OUTPUT", exception.getMessage());
    }

    private PythonPromptAiStreamGateway gateway(ExchangeFunction exchange) {
        return new PythonPromptAiStreamGateway(
                WebClient.builder().exchangeFunction(exchange).build()
        );
    }

    private PromptOptimizeRequestDTO request() {
        PromptOptimizeRequestDTO request = new PromptOptimizeRequestDTO();
        request.setOriginalPrompt("hello");
        return request;
    }
}
