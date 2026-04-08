package com.jojo.prompt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//登录成功返回对象
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录成功返回对象")
public class LoginVO {

    @Schema(description = "访问JWT Token")
    private String token;

    @Schema(description = "用户信息")
    private UserVO userInfo;
}