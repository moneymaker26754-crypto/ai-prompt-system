package com.jojo.prompt.aipromptsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class ObjectMapperInspectionTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void printObjectMappers() throws Exception {
        System.out.println("BEANS=" + applicationContext.getBeansOfType(ObjectMapper.class).keySet());
        System.out.println("PRIMARY_CLASS=" + objectMapper.getClass().getName());
        System.out.println("MAP_JSON=" + objectMapper.writeValueAsString(java.util.Map.of("k", "v")));
    }
}
