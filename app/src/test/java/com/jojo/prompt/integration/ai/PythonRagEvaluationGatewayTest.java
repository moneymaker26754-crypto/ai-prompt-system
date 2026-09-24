package com.jojo.prompt.integration.ai;

import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.integration.ai.dto.PythonRagComparisonResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonRagEvaluationGatewayTest {

    private MockRestServiceServer server;
    private PythonRagEvaluationGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://ai-service");
        server = MockRestServiceServer.bindTo(builder).build();

        AiServiceProperties properties = new AiServiceProperties();
        properties.setInternalApiKey("benchmark-secret");
        gateway = new PythonRagEvaluationGateway(
                builder.build(),
                properties,
                new ObjectMapper()
        );
    }

    @Test
    void compareCallsInternalEndpointAndMapsBothMetricSets() {
        server.expect(requestTo("http://ai-service/internal/rag/evaluate/compare"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-API-Key", "benchmark-secret"))
                .andExpect(content().json("""
                        {"dataset":"rag_eval.jsonl"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "queries": 100,
                          "exact": {
                            "recall_at_5": 0.87,
                            "recall_at_10": 0.91,
                            "mrr_at_5": 0.76,
                            "mrr_at_10": 0.78,
                            "p95_latency_ms": 65.0
                          },
                          "hnsw": {
                            "recall_at_5": 0.85,
                            "recall_at_10": 0.89,
                            "mrr_at_5": 0.75,
                            "mrr_at_10": 0.77,
                            "p95_latency_ms": 12.0
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PythonRagComparisonResponse response =
                gateway.compareExactAndHnsw("rag_eval.jsonl");

        assertEquals(100, response.queries());
        assertEquals(0.91, response.exact().recallAt10());
        assertEquals(12.0, response.hnsw().p95LatencyMs());
        server.verify();
    }

    @Test
    void compareMapsMissingIndexConflictToStableError() {
        server.expect(requestTo("http://ai-service/internal/rag/evaluate/compare"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail":"HNSW index is not available"}
                                """));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.compareExactAndHnsw("rag_eval.jsonl")
        );

        assertEquals(409, exception.getCode());
        assertEquals("RAG_HNSW_INDEX_MISSING", exception.getMessage());
    }

    @Test
    void compareMapsTransportTimeoutToStableAiTimeout() {
        server.expect(requestTo("http://ai-service/internal/rag/evaluate/compare"))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "socket detail",
                            new HttpTimeoutException("timed out")
                    );
                });

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.compareExactAndHnsw("rag_eval.jsonl")
        );

        assertEquals(504, exception.getCode());
        assertEquals("AI_TIMEOUT", exception.getMessage());
    }
}
