package com.jojo.prompt.service;

import com.jojo.prompt.dto.request.LoginDTO;
import com.jojo.prompt.dto.request.PasswordUpdateDTO;
import com.jojo.prompt.dto.request.RegisterDTO;
import com.jojo.prompt.dto.request.UserUpdateDTO;
import com.jojo.prompt.dto.response.LoginVO;
import com.jojo.prompt.dto.response.UserVO;

public interface UserService {
    //用户注册
    void register(RegisterDTO dto);
    //用户登录
    LoginVO login(LoginDTO dto);
    //获取当前登录用户信息
    UserVO getCurrentUser();
    //更新用户信息
    void UpdateInfo(UserUpdateDTO dto);
    //修改密码
    void UpdatePassword(PasswordUpdateDTO dto);
    //根据id查询用户
    UserVO getById(Long id);
}
