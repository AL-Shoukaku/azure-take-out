package com.azure.service.impl;

import com.azure.constant.MessageConstant;
import com.azure.context.BaseContext;
import com.azure.dto.PasswordEditDTO;
import com.azure.dto.UserInfoDTO;
import com.azure.dto.UserLoginDTO;
import com.azure.dto.UserRegisterDTO;
import com.azure.entity.User;
import com.azure.exception.BaseException;
import com.azure.exception.LoginFailedException;
import com.azure.mapper.UserMapper;
import com.azure.properties.JwtProperties;
import com.azure.security.PasswordHasher;
import com.azure.support.ServiceTestSupport;
import com.azure.support.WebTestConfiguration;
import com.azure.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest extends ServiceTestSupport {
    @InjectMocks private UserServiceImpl service;
    @Mock private UserMapper mapper;
    @Spy private PasswordHasher passwordHasher = new PasswordHasher();
    @Spy private JwtProperties jwtProperties = properties();
    @Captor private ArgumentCaptor<User> saved;

    private static JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setUserSecretKey(WebTestConfiguration.USER_SECRET);
        properties.setUserTtl(60000);
        return properties;
    }

    private User currentUser() {
        return User.builder().id(USER_ID).account("user42").username("测试昵称").name("测试姓名")
                .password(passwordHasher.encode("123456")).status(1).phone("13800000042")
                .sex("1").idNumber("123456789012345678").avatar("/images/user/old.png").build();
    }

    private UserRegisterDTO registration(String account, String password) {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setAccount(account);
        dto.setPassword(password);
        return dto;
    }

    private UserLoginDTO login(String account, String password) {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setAccount(account);
        dto.setPassword(password);
        return dto;
    }

    private PasswordEditDTO passwordChange(String oldPassword, String newPassword) {
        PasswordEditDTO dto = new PasswordEditDTO();
        dto.setOldPassword(oldPassword);
        dto.setNewPassword(newPassword);
        return dto;
    }

    static Stream<Arguments> emptyCredentials() {
        return Stream.of(null, "", "   ", "\t").flatMap(value -> Stream.of(
                Arguments.of(value, "123456"), Arguments.of("user42", value)));
    }

    @Test
    void registrationStoresHashedPasswordAndInitialProfile() {
        LocalDateTime before = LocalDateTime.now();
        service.register(registration("new-account", "123456"));
        verify(mapper).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getAccount()).isEqualTo("new-account");
        assertThat(user.getUsername()).startsWith("用户");
        assertThat(user.getStatus()).isEqualTo(1);
        assertThat(user.getAvatar()).isNull();
        assertThat(user.getCreateTime()).isBetween(before, LocalDateTime.now());
        assertThat(user.getUpdateTime()).isEqualTo(user.getCreateTime());
        assertThat(user.getPassword()).startsWith("$argon2id$").isNotEqualTo("123456");
        assertThat(passwordHasher.matches("123456", user.getPassword())).isTrue();
    }

    @ParameterizedTest @MethodSource("emptyCredentials")
    void registrationRejectsEmptyOriginalCredentials(String account, String password) {
        assertThatThrownBy(() -> service.register(registration(account, password)))
                .isInstanceOf(BaseException.class).hasMessage("账号和密码不能为空");
        verifyNoInteractions(mapper);
    }

    @Test
    void duplicateAccountReturnsBusinessError() {
        doThrow(new DuplicateKeyException("duplicate account")).when(mapper).save(any());
        assertThatThrownBy(() -> service.register(registration("user42", "123456")))
                .isInstanceOf(BaseException.class).hasMessage("账号已存在");
    }

    @Test
    void loginReturnsProfileAndUserSignedTokenWithConfiguredExpiration() {
        User user = currentUser();
        when(mapper.getByAccount("user42")).thenReturn(user);
        var result = service.login(login("user42", "123456"));
        assertThat(result.getId()).isEqualTo(USER_ID);
        assertThat(result.getUsername()).isEqualTo(user.getUsername());
        assertThat(result.getAvatar()).isEqualTo(user.getAvatar());
        var claims = JwtUtil.parseJWT(WebTestConfiguration.USER_SECRET, result.getToken());
        assertThat(claims.get("userId", Number.class).longValue()).isEqualTo(USER_ID);
        assertThat(claims.getExpiration().getTime()).isBetween(
                System.currentTimeMillis() + 55000, System.currentTimeMillis() + 61000);
        assertThat(claims).doesNotContainKeys("password", "account", "empId");
        assertThatThrownBy(() -> JwtUtil.parseJWT(WebTestConfiguration.ADMIN_SECRET, result.getToken()))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
        verify(mapper, never()).save(any());
    }

    @ParameterizedTest @MethodSource("emptyCredentials")
    void loginRejectsEmptyCredentialsBeforeReadingUser(String account, String password) {
        assertThatThrownBy(() -> service.login(login(account, password)))
                .isInstanceOf(LoginFailedException.class).hasMessage("账号和密码不能为空");
        verifyNoInteractions(mapper);
    }

    @Test
    void unknownAccountDoesNotCreateUser() {
        assertThatThrownBy(() -> service.login(login("unknown", "123456")))
                .isInstanceOf(LoginFailedException.class).hasMessage(MessageConstant.ACCOUNT_NOT_FOUND);
        verify(mapper, never()).save(any());
    }

    @Test
    void wrongPasswordRejectsLogin() {
        User user = currentUser();
        when(mapper.getByAccount("user42")).thenReturn(user);
        assertThatThrownBy(() -> service.login(login("user42", "wrong")))
                .isInstanceOf(LoginFailedException.class).hasMessage(MessageConstant.PASSWORD_ERROR);
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0})
    void disabledAccountCannotLogIn(Integer status) {
        User user = currentUser();
        user.setStatus(status);
        when(mapper.getByAccount("user42")).thenReturn(user);
        assertThatThrownBy(() -> service.login(login("user42", "123456")))
                .isInstanceOf(LoginFailedException.class).hasMessage(MessageConstant.ACCOUNT_LOCKED);
    }

    @Test
    void currentProfileUsesAuthenticatedIdAndIncludesNickname() {
        User user = currentUser();
        when(mapper.getById(USER_ID)).thenReturn(user);
        var profile = service.getCurrentUserInfo();
        assertThat(profile).extracting("username", "name", "phone", "avatar")
                .containsExactly(user.getUsername(), user.getName(), user.getPhone(), user.getAvatar());
    }

    @Test
    void missingCurrentUserReturnsBusinessError() {
        assertThatThrownBy(service::getCurrentUserInfo).isInstanceOf(BaseException.class).hasMessage("用户不存在");
    }

    @Test
    void profileUpdateOnlyContainsEditableFieldsAndCurrentId() {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(USER_ID);
        dto.setUsername("新的昵称");
        dto.setName("张三");
        dto.setPhone("13800000000");
        dto.setAvatar("/images/user/new.png");
        service.updateUserInfo(dto);
        verify(mapper).updateUser(saved.capture());
        User update = saved.getValue();
        assertThat(update).extracting("id", "username", "name", "phone", "avatar")
                .containsExactly(USER_ID, "新的昵称", "张三", "13800000000", "/images/user/new.png");
        assertThat(update).extracting("account", "password", "status", "createTime").containsOnlyNulls();
        assertThat(update.getUpdateTime()).isNotNull();
    }

    @Test
    void profileCannotUpdateAnotherUser() {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(43L);
        dto.setUsername("他人昵称");
        assertThatThrownBy(() -> service.updateUserInfo(dto))
                .isInstanceOf(BaseException.class).hasMessage("不能修改其他用户信息");
        verifyNoInteractions(mapper);
    }

    @Test
    void passwordChangeOnlyWritesCurrentIdNewHashAndUpdateTime() {
        User user = currentUser();
        when(mapper.getById(USER_ID)).thenReturn(user);
        service.editPassword(passwordChange("123456", " New密码123 "));
        verify(mapper).updateUser(saved.capture());
        User update = saved.getValue();
        assertThat(update.getId()).isEqualTo(USER_ID);
        assertThat(passwordHasher.matches(" New密码123 ", update.getPassword())).isTrue();
        assertThat(passwordHasher.matches("New密码123", update.getPassword())).isFalse();
        assertThat(update.getUpdateTime()).isNotNull();
        assertThat(update).extracting("account", "username", "name", "phone", "avatar", "status").containsOnlyNulls();
    }

    @ParameterizedTest @MethodSource("emptyCredentials")
    void passwordChangeRejectsEmptyPasswords(String oldPassword, String newPassword) {
        assertThatThrownBy(() -> service.editPassword(passwordChange(oldPassword, newPassword)))
                .isInstanceOf(BaseException.class).hasMessage(MessageConstant.INPUT_CAN_NOT_BE_EMPTY);
        verifyNoInteractions(mapper);
    }

    @Test
    void wrongOldPasswordDoesNotUpdateUser() {
        User user = currentUser();
        when(mapper.getById(USER_ID)).thenReturn(user);
        assertThatThrownBy(() -> service.editPassword(passwordChange("wrong", "New123")))
                .isInstanceOf(BaseException.class).hasMessage(MessageConstant.OLD_PASSWORD_ERROR);
        verify(mapper, never()).updateUser(any());
    }

    @Test
    void missingUserCannotChangePassword() {
        assertThatThrownBy(() -> service.editPassword(passwordChange("123456", "New123")))
                .isInstanceOf(BaseException.class).hasMessage(MessageConstant.USER_NOT_FOUND);
        verify(mapper, never()).updateUser(any());
    }
}
