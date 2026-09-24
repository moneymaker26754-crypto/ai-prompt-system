package com.jojo.prompt.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class RedisLuaConfig {
    @Bean("countSnapshotScript")
    public DefaultRedisScript<List> countSnapshotScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/count_snapshot.lua"));
        script.setResultType(List.class);
        return script;
    }
    @Bean("countDeductScript")
    public DefaultRedisScript<List> countDeductScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/count_deduct.lua"));
        script.setResultType(List.class);
        return script;
    }
    @Bean("unlockScript")
    public DefaultRedisScript<Long> unlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/unlock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
