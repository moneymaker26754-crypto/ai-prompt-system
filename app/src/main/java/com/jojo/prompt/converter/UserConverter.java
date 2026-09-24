package com.jojo.prompt.converter;

import cn.hutool.core.bean.BeanUtil;
import com.jojo.prompt.dto.response.UserVO;
import com.jojo.prompt.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserConverter {

    //Entity转VO（不包含密码等敏感信息）
    public UserVO toVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO vo = BeanUtil.copyProperties(user, UserVO.class);
        // 密码不返回给前端
        return vo;
    }

    //EntityList转VOList
    public List<UserVO> toVOList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }

        return users.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }
}