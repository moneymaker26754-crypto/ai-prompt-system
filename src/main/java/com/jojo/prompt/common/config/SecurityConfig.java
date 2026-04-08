package com.jojo.prompt.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 * 只使用密码加密功能，认证由 JWT 处理
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 密码加密器（BCrypt 加密）
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 关闭 Spring Security 的默认认证
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())  ///关闭CSRF
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());  //允许所有请求

        return http.build();
    }
}