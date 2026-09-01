package com.jojo.prompt.integration.ai.dto;

public record PythonRagComparisonResponse(
        int queries,
        PythonRetrievalBenchmarkMetrics exact,
        PythonRetrievalBenchmarkMetrics hnsw
) {
}
