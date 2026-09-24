package com.jojo.prompt.controller;

import com.jojo.prompt.common.result.Result;
import com.jojo.prompt.dto.request.LoginDTO;
import com.jojo.prompt.dto.request.PasswordUpdateDTO;
import com.jojo.prompt.dto.request.RegisterDTO;
import com.jojo.prompt.dto.request.UserUpdateDTO;
import com.jojo.prompt.dto.response.LoginVO;
import com.jojo.prompt.dto.response.UserVO;
import com.jojo.prompt.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户认证", description = "注册，登录，个人信息管理")
public class UserController {
    private final UserService userService;
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO dto) {
        userService.register(dto);
        return Result.success("register success", null);
    }
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto, HttpServletRequest request) {
        LoginVO loginVO = userService.login(dto, request);
        return Result.success("login success",loginVO);
    }
    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/profile")
    public Result<UserVO> getProfile() {
        UserVO userVO = userService.getCurrentUser();
        return Result.success(userVO);
    }
    @Operation(summary = "更新用户信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody @Valid UserUpdateDTO dto) {
        userService.UpdateInfo(dto);
        return Result.success("update success", null);
    }
    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody @Valid PasswordUpdateDTO dto) {
        userService.UpdatePassword(dto);
        return Result.success("update success", null);
    }
}
