package com.jojo.prompt.common.config;

import com.jojo.prompt.infra.bloom.RedisBloomFilter;
import com.jojo.prompt.infra.bloom.RedisBloomFilterFactory;
import com.jojo.prompt.infra.dimension.RequestDimension;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 主项目对 prompt-infra-spring-boot-starter 的接入配置：
 * 提供 RequestDimension SPI 实现与 Prompt ID 布隆过滤器。
 */
@Configuration
public class InfraIntegrationConfig {

    /** Prompt ID 布隆过滤器（防缓存穿透第一道防线，与空值缓存互补） */
    @Bean
    public RedisBloomFilter promptIdBloomFilter(RedisBloomFilterFactory factory) {
        return factory.create("bloom:prompt:id");
    }

    /** 从 HttpServletRequest / SecurityContext 解析请求维度 */
    @Bean
    public RequestDimension requestDimension() {
        return new RequestDimension() {
            @Override
            public String currentIp() {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs == null) {
                    return null;
                }
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }

            @Override
            public String currentUser() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()
                        && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                    return String.valueOf(auth.getPrincipal());
                }
                return null;
            }
        };
    }
}
