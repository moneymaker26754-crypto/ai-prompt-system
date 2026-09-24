package com.jojo.prompt.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonRetrievalBenchmarkMetrics(
        @JsonProperty("recall_at_5") double recallAt5,
        @JsonProperty("recall_at_10") double recallAt10,
        @JsonProperty("mrr_at_5") double mrrAt5,
        @JsonProperty("mrr_at_10") double mrrAt10,
        @JsonProperty("p95_latency_ms") double p95LatencyMs
) {
}
