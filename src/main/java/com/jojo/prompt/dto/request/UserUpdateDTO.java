package com.jojo.prompt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

//更新用户信息请求参数
@Data
@Schema(description = "更新用户信息请求参数")
public class UserUpdateDTO {

    @Length(max = 20, message = "昵称最长20位")
    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;
}