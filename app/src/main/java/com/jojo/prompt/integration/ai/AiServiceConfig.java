package com.jojo.prompt.integration.ai;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
// 配置HTTP客户端连接参数
public class AiServiceConfig {

    @Bean("pythonAiRestClient")
    public RestClient pythonAiRestClient(
            RestClient.Builder builder,
            AiServiceProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1) // 避免协议升级问题
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder
                .baseUrl(properties.getBaseUrl()) // // http://127.0.0.1:8000
                .requestFactory(requestFactory)
                .build();
    }

    @Bean("pythonAiWebClient")
    public WebClient pythonAiWebClient(
            WebClient.Builder builder,
            AiServiceProperties properties
    ) {
        reactor.netty.http.client.HttpClient httpClient =
                reactor.netty.http.client.HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.getConnectTimeout().toMillis())
                )
                .responseTimeout(properties.getReadTimeout());
        return builder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean("pythonRagRestClient")
    public RestClient pythonRagRestClient(
            RestClient.Builder builder,
            AiServiceProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getRagEvaluationReadTimeout());

        return builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
