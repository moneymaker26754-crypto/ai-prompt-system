package com.jojo.prompt.common.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PromptStatus {
    DISABLED(0, "DISABLED"),
    ENABLED(1, "ENABLED");

    @EnumValue
    private final Integer code;
    @JsonValue
    private final String desc;

    public static PromptStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (PromptStatus status : PromptStatus.values()) {
            if(status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknow status code: " + code);
    }
}
