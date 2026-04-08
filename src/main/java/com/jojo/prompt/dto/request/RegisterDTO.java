package com.jojo.prompt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

//用户注册请求参数
@Data
@Schema(description = "用户注册请求参数")
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,16}$", message = "用户名只能包含字母、数字、下划线，长度4-16位")
    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度6-20位")
    @Schema(description = "密码", example = "123456")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Length(max = 20, message = "昵称最长20位")
    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;
}