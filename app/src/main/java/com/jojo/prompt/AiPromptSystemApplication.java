package com.jojo.prompt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
//扫描mapper接口
@MapperScan("com.jojo.prompt.mapper")
public class AiPromptSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPromptSystemApplication.class, args);
    }

}
