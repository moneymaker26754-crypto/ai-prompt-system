package com.jojo.prompt.integration.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiServicePropertiesTest {

    @Test
    void defaultReadTimeoutLeavesOuterBudgetAbovePythonTimeout() {
        AiServiceProperties properties = new AiServiceProperties();

        assertEquals(Duration.ofSeconds(135), properties.getReadTimeout());
    }

    @Test
    void ragEvaluationHasAnIndependentLongRunningTimeout() {
        AiServiceProperties properties = new AiServiceProperties();

        assertEquals(Duration.ofMinutes(10), properties.getRagEvaluationReadTimeout());
    }
}
