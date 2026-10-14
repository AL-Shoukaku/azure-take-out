package com.sky.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class JwtUtil {

    /**
     * 将配置中的 Base64 文本转换为签名密钥。
     *
     * @param secretKey 配置文件中的密钥，必须为有效的标准 Base64 文本
     * @return
     */
    public static SecretKey keyFromBase64(String secretKey) {
        // 密钥为空
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("JWT 签名密钥不能为空");
        }

        // 将 Base64 的密钥解码为字节数组
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT 签名密钥必须为有效的标准 Base64 文本");
        }

        // 解码后的密钥不足 32 字节时，会抛出 WeakKeyException。
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成jwt
     * 使用Hs256算法, 私匙使用固定秘钥
     *
     * @param secretKey jwt秘钥
     * @param ttlMillis jwt过期时间(毫秒)
     * @param claims    设置的信息
     * @return
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        SecretKey key = keyFromBase64(secretKey);
        Date expiration = new Date(System.currentTimeMillis() + ttlMillis);

        return Jwts.builder()
                .claims(claims)
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Token解密
     *
     * @param secretKey jwt秘钥 此秘钥一定要保留好在服务端, 不能暴露出去, 否则sign就可以被伪造, 如果对接多个客户端建议改造成多个
     * @param token     加密后的token
     * @return
     */
    public static Claims parseJWT(String secretKey, String token) {
        SecretKey key = keyFromBase64(secretKey);

        // 只接受HS256算法的签名
        return Jwts.parser()
                .verifyWith(key)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
