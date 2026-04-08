package com.jojo.prompt.common.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatus {
    DISABLE(0, "DISABLE"),
    ENABLE(1, "ENABLE");
    @EnumValue
    private final Integer code;
    @JsonValue
    private final String desc;
    public static UserStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatus status : UserStatus.values()) {
            if(status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid user status code: " + code);
    }
}
