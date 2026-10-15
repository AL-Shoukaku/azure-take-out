package com.sky.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * 统一生成、验证密码哈希，并在迁移期间兼容数据库中的旧 MD5。
 * 新密码始终使用 Argon2id 编码器，MD5 分支只用于验证旧数据。
 */
@Component
public class PasswordHasher {

    // 旧实现生成的是 32 位小写十六进制 MD5，不把未知格式当成旧密码处理。
    private static final Pattern LEGACY_MD5 = Pattern.compile("[0-9a-f]{32}");

    // 先检查完整结构，拒绝缺失字段、被截断或带有多余尾部内容的哈希。
    // v=版本号在 Argon2 编码规范中可省略；具体参数和 Base64 内容仍由编码器验证。
    private static final Pattern ARGON2ID_HASH = Pattern.compile(
            "\\$argon2id\\$(?:v=[0-9]+\\$)?m=[0-9]+,t=[0-9]+,p=[0-9]+"
                    + "\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+");

    // 在组件创建时初始化编码器，后续调用复用；@Component 默认由 Spring 按单例管理。
    // Spring 的这个实现生成 Argon2id 哈希，随机盐和具体计算交给底层库处理。
    private final PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(
            16,         // 每次编码随机生成 16 字节的盐。
            32,         // 输出 32 字节的哈希；存储时还会编码为 Base64 文本。
            1,          // 并行度，表示算法使用的内存通道数。
            19 * 1024,  // 单次计算的内存参数，单位 KiB，即 19 MiB。
            2           // 迭代次数；部署前应结合实际机器测量耗时和并发开销。
    );

    /**
     * 为新密码生成完整编码字符串，其中已经包含算法、参数、随机盐和哈希。
     * 每次调用都会使用新盐，相同密码也会得到不同结果；数据库应保存完整字符串。
     * 密码长度、字符范围等业务规则由调用方校验。
     */
    public String encode(String rawPassword) {
        Assert.notNull(rawPassword, "原始密码不能为空");
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 验证用户输入的原始密码与数据库中的哈希是否匹配。
     * 不要用 encode(rawPassword).equals(storedHash) 代替，因为随机盐会改变编码结果。
     */
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }

        if (isLegacyMd5(storedHash)) {
            String actual = DigestUtils.md5DigestAsHex(
                    rawPassword.getBytes(StandardCharsets.UTF_8));
            // 两个值都是固定长度的 ASCII 摘要，使用摘要字节比较而非普通字符串比较。
            return MessageDigest.isEqual(
                    actual.getBytes(StandardCharsets.US_ASCII),
                    storedHash.getBytes(StandardCharsets.US_ASCII));
        }

        if (!isArgon2id(storedHash)) {
            return false;
        }

        try {
            // 编码器从已保存的字符串中取出原来的盐和参数，再验证本次输入。
            return passwordEncoder.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException e) {
            // 非法参数或损坏的编码验证失败；不记录密码或完整哈希。
            return false;
        }
    }

    /**
     * 判断已经验证成功的密码哈希是否需要重新编码。
     * 旧 MD5 必须升级；Argon2id 的内存或迭代参数低于当前配置时也需要升级。
     * 本方法不验证密码，调用方应先 matches 成功，再用本次原始密码生成新哈希。
     */
    public boolean needsUpgrade(String storedHash) {
        if (isLegacyMd5(storedHash)) {
            return true;
        }
        if (!isArgon2id(storedHash)) {
            return false;
        }
        try {
            return passwordEncoder.upgradeEncoding(storedHash);
        } catch (IllegalArgumentException e) {
            // 无法识别的存储值不能作为自动升级的依据。
            return false;
        }
    }

    private boolean isLegacyMd5(String storedHash) {
        return storedHash != null && LEGACY_MD5.matcher(storedHash).matches();
    }

    private boolean isArgon2id(String storedHash) {
        return storedHash != null && ARGON2ID_HASH.matcher(storedHash).matches();
    }
}
