package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.entity.User;
import com.sky.vo.UserLoginVO;

public interface UserService {

    /**
     * 用户注册
     * @param userRegisterDTO   用户注册信息
     */
    void register(UserRegisterDTO userRegisterDTO);

    /**
     * 用户登录
     * @param userLoginDTO  用户登录信息
     * @return              登录成功的用户信息
     */
    UserLoginVO login(UserLoginDTO userLoginDTO);
}
