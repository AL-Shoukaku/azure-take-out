package com.sky.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 微信用户登录
     * @param userLoginDTO
     * @return
     */
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        // 向微信服务器发送请求，获取openid
        String openid = getOpenid(userLoginDTO.getCode());

        // 检查 openid
        if (!StringUtils.hasText(openid)) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        // 检查该用户是否为新用户
        User user = userMapper.getUserByOpenid(openid);
        if (user == null) {
            // 新用户，保存用户信息
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.save(user);
        }
        return user;
    }

    /**
     * 向微信服务器发送请求，获取 openid
     * @param code
     * @return
     */
    private String getOpenid(String code) {
        // 准备要发送的信息
        Map<String, String> params = new HashMap<>();
        params.put("appid", weChatProperties.getAppid());
        params.put("secret", weChatProperties.getSecret());
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN_URL, params);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode response = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(json);
            if (response == null || !response.isObject()) {
                return null;
            }
            JsonNode openid = response.get("openid");
            return openid != null && openid.isTextual() ? openid.textValue() : null;
        } catch (JsonProcessingException e) {
            log.warn("微信登录响应无法解析");
            return null;
        }
    }
}
