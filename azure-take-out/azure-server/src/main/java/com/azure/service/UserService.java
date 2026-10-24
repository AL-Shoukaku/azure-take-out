package com.azure.service;

import com.azure.dto.PasswordEditDTO;
import com.azure.dto.UserInfoDTO;
import com.azure.dto.UserLoginDTO;
import com.azure.dto.UserRegisterDTO;
import com.azure.vo.UserInfoVO;
import com.azure.vo.UserLoginVO;

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

    /**
     * 更新用户信息
     * @param userInfoDTO   用户信息
     */
    void updateUserInfo(UserInfoDTO userInfoDTO);

    /**
     * 查询当前登录用户的信息
     * @return      用户信息
     */
    UserInfoVO getCurrentUserInfo();

    /**
     * 校验旧密码后修改当前登录用户的密码
     * @param passwordEditDTO   用户修改密码信息，包括新旧密码
     */
    void editPassword(PasswordEditDTO passwordEditDTO);
}
