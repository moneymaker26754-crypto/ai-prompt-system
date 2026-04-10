package com.jojo.prompt.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

@Configuration
public class RedisLuaConfig {
    @Bean("redisScript")
    public DefaultRedisScript<List> drainPromptCountsToProcessingScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(
                new ResourceScriptSource(
                        new ClassPathResource("lua/count.lua")
                )
        );
        script.setResultType(List.class);
        return script;
    }
}
