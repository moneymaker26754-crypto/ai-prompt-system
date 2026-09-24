package com.jojo.prompt.minimq.spring;

import com.jojo.prompt.minimq.net.MiniMqClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * mini-mq-spring-boot-starter 自动装配。
 *
 * <p>默认关闭（prompt.mini-mq.enabled=false，matchIfMissing=false）：
 * 业务方引入依赖但不开配置时零影响，符合「渐进替换」安全要求。</p>
 */
@AutoConfiguration
@ConditionalOnClass(MiniMqClient.class)
@ConditionalOnProperty(prefix = "prompt.mini-mq", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MiniMqProperties.class)
public class MiniMqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MiniMqTemplate miniMqTemplate(MiniMqProperties properties) {
        return new MiniMqTemplate(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MiniMqListenerContainer miniMqListenerContainer(
            MiniMqTemplate template, MiniMqProperties properties) {
        return new MiniMqListenerContainer(template, properties);
    }

    /** actuator 健康探针（无 actuator 时该 bean 不生效，模板本身不受影响） */
    @Bean
    @ConditionalOnMissingBean(name = "miniMqHealthIndicator")
    @ConditionalOnClass(HealthIndicator.class)
    public HealthIndicator miniMqHealthIndicator(MiniMqTemplate template) {
        return () -> template.isConnected()
                ? Health.up().withDetail("mini-mq", "connected").build()
                : Health.down().withDetail("mini-mq", "disconnected").build();
    }
}
