package com.jojo.prompt.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jojo.prompt.common.constant.UserStatus;
import com.jojo.prompt.common.exception.BusinessException;
import com.jojo.prompt.common.utils.JwtUtil;
import com.jojo.prompt.common.utils.UserContext;
import com.jojo.prompt.converter.UserConverter;
import com.jojo.prompt.dto.request.LoginDTO;
import com.jojo.prompt.dto.request.PasswordUpdateDTO;
import com.jojo.prompt.dto.request.RegisterDTO;
import com.jojo.prompt.dto.request.UserUpdateDTO;
import com.jojo.prompt.dto.response.LoginVO;
import com.jojo.prompt.dto.response.UserVO;
import com.jojo.prompt.entity.User;
import com.jojo.prompt.mapper.UserMapper;
import com.jojo.prompt.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserConverter userConverter;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );
        if (count > 0) {
            throw new BusinessException("username already exist");
        }

        count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, dto.getEmail())
        );
        if (count > 0) {
            throw new BusinessException("email already exist");
        }

        User user = BeanUtil.copyProperties(dto, User.class);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(UserStatus.ENABLE);
        userMapper.insert(user);

        log.info("register username:{}, userId:{}", user.getUsername(), user.getId());
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );
        if (user == null) {
            throw new BusinessException("username or password is incorrect");//防止泄露用户是否存在
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("username or password is incorrect");//防止泄露密码
        }
        if (user.getStatus() == UserStatus.DISABLE) {
            throw new BusinessException("user is DISABLE");
        }

        String token = jwtUtil.createToken(user.getId(), user.getUsername());
        UserVO userVO = userConverter.toVO(user);
        log.info("login user:{}, userId={}", dto.getUsername(), user.getId());
        return new LoginVO(token, userVO);
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = requireCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("user not found");
        }
        return userConverter.toVO(user);
    }

    @Override
    public void UpdateInfo(UserUpdateDTO dto) {
        Long userId = requireCurrentUserId();
        User existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException("user not found");
        }

        if (dto.getEmail() != null && !dto.getEmail().equals(existing.getEmail())) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, dto.getEmail())
            );
            if (count > 0) {
                throw new BusinessException("email already exist");
            }
        }

        User user = new User();
        user.setId(userId);
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        userMapper.updateById(user);

        log.info("update success userId:{}", userId);
    }

    @Override
    public void UpdatePassword(PasswordUpdateDTO dto) {
        Long userId = requireCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("user not found");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException("two new passwords do not match");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException("new password cannot be the same as old password");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("old password not match");
        }

        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(updateUser);

        log.info("update password success userId:{}", userId);
    }

    @Override
    public UserVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("user not found");
        }
        return userConverter.toVO(user);
    }

    private Long requireCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "not logged in, please log in first");
        }
        return userId;
    }
}
