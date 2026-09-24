package com.jojo.prompt.integration.ai;

import com.jojo.prompt.integration.ai.dto.PythonRagComparisonResponse;

public interface RagEvaluationGateway {

    PythonRagComparisonResponse compareExactAndHnsw(String dataset);
}
