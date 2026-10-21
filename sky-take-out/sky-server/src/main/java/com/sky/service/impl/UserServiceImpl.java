package com.sky.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
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
import com.sky.utils.HttpClientUtil;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
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
}
