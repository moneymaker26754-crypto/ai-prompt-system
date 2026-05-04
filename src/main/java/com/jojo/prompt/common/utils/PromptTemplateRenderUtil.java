package com.jojo.prompt.common.utils;

import org.springframework.util.StringUtils;

import java.util.Map;

public class PromptTemplateRenderUtil {

    private PromptTemplateRenderUtil(){
    }

    public static String render(String template, Map<String, String> variables) {
        if(!StringUtils.hasText(template)) {
            return "";
        }

        String result = template;
        for(Map.Entry<String, String> entry : variables.entrySet()) {
            String key = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace(key, value);
        }
        return result;
    }
}
