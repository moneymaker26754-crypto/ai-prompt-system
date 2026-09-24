package com.jojo.prompt.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.filter.RequestIdFilter;
import com.jojo.prompt.entity.PromptTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonPromptAiGatewayTest {

    private MockRestServiceServer server;
    private PythonPromptAiGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://ai-service");

        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        gateway = new PythonPromptAiGateway(
                builder.build(),
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        org.slf4j.MDC.clear();
    }

    @Test
    void analyzeShouldCallPythonService() {
        server.expect(
                        requestTo(
                                "http://ai-service/v1/prompts/analyze"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "original_prompt": "帮我写文章",
                          "system_prompt": "你是专业编辑"
                        }
                        """))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "analysis": "缺少明确的输出结构",
                                  "model": "qwen3.5:9b"
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        PromptTemplate template = new PromptTemplate();
        template.setSystemPrompt("你是专业编辑");

        PromptAnalyzeResult result =
                gateway.analyze(
                        "帮我写文章",
                        template
                );

        assertEquals(
                "缺少明确的输出结构",
                result.analysis()
        );

        assertEquals(
                "qwen3.5:9b",
                result.model()
        );

        server.verify();
    }

    @Test
    void analyzeShouldConvertPythonUnavailableError() {

        server.expect(
                requestTo(
                        "http://ai-service/v1/prompts/analyze"
                )
        ).andRespond(
                withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                              "code": "AI_UNAVAILABLE",
                              "message": "Unable to connect to Ollama"
                            }
                            """)
        );

        PromptTemplate template = new PromptTemplate();

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> gateway.analyze(
                                "hello",
                                template
                        )
                );

        assertEquals(503, exception.getCode());
        assertEquals("AI_UNAVAILABLE", exception.getMessage());

        server.verify();
    }

    @Test
    void analyzeShouldMapPythonTimeoutToStableError() {
        server.expect(requestTo("http://ai-service/v1/prompts/analyze"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "AI_TIMEOUT",
                                  "message": "Ollama request timed out"
                                }
                                """));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.analyze("hello", new PromptTemplate())
        );

        assertEquals(504, exception.getCode());
        assertEquals("AI_TIMEOUT", exception.getMessage());
        server.verify();
    }

    @Test
    void analyzeShouldMapTransportTimeoutWithoutLeakingDetails() {
        server.expect(requestTo("http://ai-service/v1/prompts/analyze"))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "low-level timeout detail",
                            new HttpTimeoutException("socket timed out")
                    );
                });

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateway.analyze("hello", new PromptTemplate())
        );

        assertEquals(504, exception.getCode());
        assertEquals("AI_TIMEOUT", exception.getMessage());
    }

    @Test
    void analyzeShouldForwardRequestIdWhenPresent() {
        org.slf4j.MDC.put(RequestIdFilter.MDC_KEY, "observability-test-1");
        server.expect(requestTo("http://ai-service/v1/prompts/analyze"))
                .andExpect(header(RequestIdFilter.HEADER, "observability-test-1"))
                .andRespond(withSuccess(
                        """
                                {"analysis":"ok","model":"qwen3.5:9b"}
                                """,
                        MediaType.APPLICATION_JSON
                ));

        gateway.analyze("hello", new PromptTemplate());

        server.verify();
    }

    @Test
    void reviewShouldCallPythonService() {

        server.expect(
                        requestTo(
                                "http://ai-service/v1/prompts/review"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
            {
              "original_prompt": "hello",
              "optimized_prompt": "better hello"
            }
            """))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "score": 90,
                                  "risk_level": "LOW",
                                  "changed_intent": false,
                                  "review_comment": "保持原意",
                                  "model": "qwen3.5:9b"
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        PromptReviewResult result =
                gateway.review(
                        "hello",
                        "better hello"
                );

        assertEquals(90, result.score());
        assertEquals("LOW", result.riskLevel());
        assertFalse(result.changedIntent());

        server.verify();
    }
}
