package com.jojo.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationYamlTest {

    @Test
    void mainConfigurationIsValidYaml() throws Exception {
        var propertySources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));

        assertThat(propertySources).isNotEmpty();
    }
}
