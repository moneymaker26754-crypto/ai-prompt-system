package com.jojo.prompt.dto.response;

import com.jojo.prompt.common.constant.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

//用户返回对象（脱敏,不包含密码）
@Data
@Schema(description = "用户信息返回对象")
public class UserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "用户状态")
    private UserStatus status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}