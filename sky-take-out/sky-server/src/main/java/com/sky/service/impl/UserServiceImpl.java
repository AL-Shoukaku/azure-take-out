package com.sky.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.PasswordEditDTO;
import com.sky.dto.UserInfoDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.JwtProperties;
import com.sky.properties.WeChatProperties;
import com.sky.security.PasswordHasher;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserInfoVO;
import com.sky.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 用户注册
     * @param userRegisterDTO   用户注册信息
     */
    @Override
    public void register(UserRegisterDTO userRegisterDTO) {
        String account = userRegisterDTO.getAccount();
        String password = passwordHasher.encode(userRegisterDTO.getPassword());
        String username = "用户" + System.currentTimeMillis();
        int status = StatusConstant.ENABLE; // 1表示启用，0表示禁用
        LocalDateTime now = LocalDateTime.now();

        // 检查账号和密码是否为空
        if (!StringUtils.hasText(account)
                || !StringUtils.hasText(password)) {
            throw new LoginFailedException("账号和密码不能为空");
        }

        User user = User.builder()
                .account(account)
                .password(password)
                .username(username)
                .status(status)
                .createTime(now)
                .updateTime(now)
                .build();

        try {
            userMapper.save(user);
        } catch (DuplicateKeyException e) {
            log.error("用户注册失败", e);
            throw new BaseException("账号已存在");
        }
    }

    /**
     * 用户登录
     */
    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {
        String account = userLoginDTO.getAccount();
        String password = userLoginDTO.getPassword();

        // 检查账号和密码是否为空
        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)) {
            throw new LoginFailedException("账号和密码不能为空");
        }

        User user = userMapper.getByAccount(account);
        // 处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (user == null) {
            throw new LoginFailedException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (!passwordHasher.matches(password, user.getPassword())) {
            throw new LoginFailedException(MessageConstant.PASSWORD_ERROR);
        }
        if (!StatusConstant.ENABLE.equals(user.getStatus())) {
            throw new LoginFailedException(MessageConstant.ACCOUNT_LOCKED);
        }

        // 无异常，登录成功，生成 token 与 UserLoginVO
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        return UserLoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .token(token)
                .build();
    }

    /**
     * 更新用户信息
     * @param userInfoDTO   用户信息
     */
    @Override
    public void updateUserInfo(UserInfoDTO userInfoDTO) {
        if (!BaseContext.getCurrentId().equals(userInfoDTO.getId())) {
            throw new BaseException("不能修改其他用户信息");
        }

        User user = new User();
        BeanUtils.copyProperties(userInfoDTO, user);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateUser(user);
    }

    /**
     * 查询当前用户的信息
     * @return      用户信息
     */
    @Override
    public UserInfoVO getCurrentUserInfo() {
        Long id = BaseContext.getCurrentId();
        User user = userMapper.getById(id);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(user, userInfoVO);
        return userInfoVO;
    }

    /**
     * 校验旧密码后修改当前登录用户的密码
     * @param passwordEditDTO   用户修改密码信息，包括新旧密码
     */
    @Override
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        // 输入不能为空
        String oldPassword = passwordEditDTO.getOldPassword();
        String newPassword = passwordEditDTO.getNewPassword();
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new BaseException(MessageConstant.INPUT_CAN_NOT_BE_EMPTY);
        }

        // 根据当前 id 获取用户并检查是否为空
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.USER_NOT_FOUND);
        }
        // 校验输入的旧密码是否正确
        String storeHash = user.getPassword();
        if (!passwordHasher.matches(oldPassword, storeHash)) {
            throw new BaseException(MessageConstant.OLD_PASSWORD_ERROR);
        }

        // 校验通过，更新密码
        String newHash = passwordHasher.encode(newPassword);
        User update = User.builder()
                .id(userId)
                .password(newHash)
                .updateTime(LocalDateTime.now())
                .build();
        userMapper.updateUser(update);
    }
}
