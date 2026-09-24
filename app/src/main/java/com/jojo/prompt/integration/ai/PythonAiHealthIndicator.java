package com.jojo.prompt.integration.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonAiHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    public PythonAiHealthIndicator(@Qualifier("pythonAiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Health health() {
        try {
            restClient.get()
                    .uri("/v1/health/ready")
                    .retrieve()
                    .toBodilessEntity();
            return Health.up().build();

        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
