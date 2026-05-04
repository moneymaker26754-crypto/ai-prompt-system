package com.jojo.prompt.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    @Qualifier("eventExecutor")
    private final ThreadPoolTaskExecutor eventExecutor;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(new DelegatingSecurityContextAsyncTaskExecutor(eventExecutor));
    }
}
