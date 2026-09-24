package com.jojo.prompt.common.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PromptVisibility {
    PUBLIC("public", "PUBLIC"),
    PRIVATE("private", "PRIVATE");

    @EnumValue
    @JsonValue
    private final String code;

    private final String desc;

    public static PromptVisibility of(String code) {
        if (code == null) {
            return null;
        }
        for (PromptVisibility visibility : values()) {
            if (visibility.code.equals(code)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown visibility" + code);
    }
}
