package com.jojo.prompt.integration.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@Data
@ConfigurationProperties(prefix = "app.ai.python")
// 绑定配置文件中的 app.ai.python.* 属性
public class AiServiceProperties {

    private String baseUrl = "http://127.0.0.1:8000";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(135);

    private String internalApiKey;

    private Duration ragEvaluationReadTimeout = Duration.ofMinutes(10);
}
