package com.sky.config;

import com.sky.properties.JwtProperties;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;
import java.util.stream.Stream;

import static com.sky.support.WebTestConfiguration.ADMIN_SECRET;
import static com.sky.support.WebTestConfiguration.USER_SECRET;
import static org.assertj.core.api.Assertions.assertThat;

/** 使用实际配置绑定和初始化回调验证密钥，不加载数据库、Redis 或本地私有配置。 */
class JwtConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfiguration {
    }

    @Test
    void validSeparateSecretsAllowStartup() {
        runner.withPropertyValues(
                "sky.jwt.admin-secret-key=" + ADMIN_SECRET,
                "sky.jwt.user-secret-key=" + USER_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.getAdminSecretKey()).isEqualTo(ADMIN_SECRET);
            assertThat(properties.getUserSecretKey()).isEqualTo(USER_SECRET);
        });
    }

    static Stream<Arguments> invalidSecrets() {
        return Stream.of("admin-secret-key", "user-secret-key").flatMap(property -> Stream.of(
                Arguments.of(property, null, IllegalArgumentException.class),
                Arguments.of(property, "", IllegalArgumentException.class),
                Arguments.of(property, "invalid-base64", IllegalArgumentException.class),
                Arguments.of(property, "aXRjYXN0", WeakKeyException.class),
                Arguments.of(property, Base64.getEncoder().encodeToString(new byte[31]), WeakKeyException.class)
        ));
    }

    @ParameterizedTest
    @MethodSource("invalidSecrets")
    void invalidSecretRejectsStartup(String property, String value,
                                     Class<? extends Throwable> expectedCause) {
        String otherProperty = property.equals("admin-secret-key")
                ? "sky.jwt.user-secret-key=" + USER_SECRET
                : "sky.jwt.admin-secret-key=" + ADMIN_SECRET;
        ApplicationContextRunner configured = runner.withPropertyValues(otherProperty);
        if (value != null) {
            configured = configured.withPropertyValues("sky.jwt." + property + "=" + value);
        }
        configured.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(expectedCause);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void identicalDecodedSecretsRejectStartup(boolean padded) {
        // 同一把密钥的带填充和不带填充 Base64 表示都应被识别。
        String userSecret = padded ? ADMIN_SECRET : ADMIN_SECRET.substring(0, ADMIN_SECRET.length() - 1);
        runner.withPropertyValues(
                "sky.jwt.admin-secret-key=" + ADMIN_SECRET,
                "sky.jwt.user-secret-key=" + userSecret).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("管理端和用户端必须使用不同的 JWT 签名密钥");
        });
    }
}
