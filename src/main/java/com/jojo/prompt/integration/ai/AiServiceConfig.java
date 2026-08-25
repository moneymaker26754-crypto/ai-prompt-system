package com.jojo.prompt.integration.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
// 配置HTTP客户端连接参数
public class AiServiceConfig {

    @Bean("pythonAiRestClient")
    public RestClient pythonAiRestClient(
            AiServiceProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1) // 避免协议升级问题
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl()) // // http://127.0.0.1:8000
                .requestFactory(requestFactory)
                .build();
    }
}
