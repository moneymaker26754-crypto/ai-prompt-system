package com.jojo.prompt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

//修改密码请求参数
@Data
@Schema(description = "修改密码请求参数")
public class PasswordUpdateDTO {

    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码", example = "123456")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度6-20位")
    @Schema(description = "新密码", example = "654321")
    private String newPassword;


    @NotBlank(message = "新密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度6-20位")
    @Schema(description = "确认新密码", example = "654321")
    private String confirmPassword;
}