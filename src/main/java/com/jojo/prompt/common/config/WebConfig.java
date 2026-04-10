package com.jojo.prompt.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

//    private final AuthInterceptor authInterceptor;
//
//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(authInterceptor)
//                .addPathPatterns("/api/**")
//                .excludePathPatterns(
//                        "/api/user/register",
//                        "/api/user/login",
//                        "/doc.html",
//                        "/swagger-resources/**",
//                        "/v3/api-docs/**",
//                        "/webjars/**",
//                        "/druid/**"
//                );
//    }
}
