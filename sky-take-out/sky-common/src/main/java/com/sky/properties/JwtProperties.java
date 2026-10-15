package com.sky.properties;

import com.sky.utils.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;

@Component
@ConfigurationProperties(prefix = "sky.jwt")
@Data
public class JwtProperties {

    /**
     * 管理端员工生成jwt令牌相关配置
     */
    private String adminSecretKey;
    private long adminTtl;
    private String adminTokenName;

    /**
     * 用户端微信用户生成jwt令牌相关配置
     */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;

    @PostConstruct
    public void validateSecretKeys() {
        SecretKey adminKey;
        SecretKey userKey;
        try {
            adminKey = JwtUtil.keyFromBase64(adminSecretKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("sky.jwt.admin-secret-key 配置无效", ex);
        }
        try {
            userKey = JwtUtil.keyFromBase64(userSecretKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("sky.jwt.user-secret-key 配置无效", ex);
        }
        if (Arrays.equals(adminKey.getEncoded(), userKey.getEncoded())) {
            throw new IllegalStateException("管理端和用户端必须使用不同的 JWT 签名密钥");
        }
    }
}
