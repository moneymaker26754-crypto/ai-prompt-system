package com.jojo.prompt.common.config;

import com.github.xiaoymin.knife4j.core.conf.GlobalConstants;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jSetting;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer;
import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Boot 3.5.x + springdoc 2.8.x 下，Knife4j 4.5.0 自带的 customizer 会调用
 * SpringDocConfigProperties#getGroupConfigs():List，和当前 springdoc 返回的 Set
 * 签名不兼容。这里保留 x-openapi 扩展注入，但跳过有问题的 addOrderExtension。
 */
@Configuration
@ConditionalOnProperty(name = "knife4j.enable", havingValue = "true")
public class Knife4jOpenApiCustomizerConfig {

    @Bean
    public Knife4jOpenApiCustomizer knife4jOpenApiCustomizer(
            Knife4jProperties knife4jProperties,
            SpringDocConfigProperties springDocConfigProperties
    ) {
        return new Knife4jOpenApiCustomizer(knife4jProperties, springDocConfigProperties) {
            @Override
            public void customise(OpenAPI openApi) {
                if (!knife4jProperties.isEnable()) {
                    return;
                }

                Knife4jSetting setting = knife4jProperties.getSetting();
                OpenApiExtensionResolver extensionResolver =
                        new OpenApiExtensionResolver(setting, knife4jProperties.getDocuments());
                extensionResolver.start();

                Map<String, Object> extensions = new HashMap<>();
                extensions.put(GlobalConstants.EXTENSION_OPEN_SETTING_NAME, setting);
                extensions.put(GlobalConstants.EXTENSION_OPEN_MARKDOWN_NAME, extensionResolver.getMarkdownFiles());
                openApi.addExtension(GlobalConstants.EXTENSION_OPEN_API_NAME, extensions);
            }
        };
    }
}
