package com.sky.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.json.JacksonObjectMapper;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.support.ServiceTestSupport;
import com.sky.utils.HttpClientUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest extends ServiceTestSupport {
    @InjectMocks private UserServiceImpl service;
    @Mock private UserMapper mapper;
    @Mock private WeChatProperties properties;
    @Spy private ObjectMapper objectMapper = new JacksonObjectMapper();
    @Captor private ArgumentCaptor<User> user;

    private UserLoginDTO request() { UserLoginDTO dto = new UserLoginDTO(); dto.setCode("one-time-code"); return dto; }
    private Map<String,String> params() {
        Map<String,String> p = new HashMap<>(); p.put("appid","test-app"); p.put("secret","test-secret");
        p.put("js_code","one-time-code"); p.put("grant_type","authorization_code"); return p;
    }
    @BeforeEach void configureWechat() { when(properties.getAppid()).thenReturn("test-app"); when(properties.getSecret()).thenReturn("test-secret"); }
    @Test void existingUserIsNotRecreated() {
        User existing = User.builder().id(7L).openid("openid-7").build();
        when(mapper.getUserByOpenid("openid-7")).thenReturn(existing);
        try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
            http.when(() -> HttpClientUtil.doGet(UserServiceImpl.WX_LOGIN_URL,params()))
                    .thenReturn("{\"openid\":\"openid-7\",\"session_key\":\"test-session\",\"unionid\":\"test-union\",\"futureField\":1}");
            assertThat(service.wxLogin(request())).isSameAs(existing);
            http.verify(() -> HttpClientUtil.doGet(UserServiceImpl.WX_LOGIN_URL,params()));
        }
        verify(mapper,never()).save(any());
    }
    @Test void firstLoginCreatesUserAndReturnsGeneratedId() {
        doAnswer(i -> { ((User)i.getArgument(0)).setId(88L); return null; }).when(mapper).save(any());
        LocalDateTime before = LocalDateTime.now();
        try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
            http.when(() -> HttpClientUtil.doGet(anyString(),anyMap())).thenReturn("{\"openid\":\"new-openid\"}");
            User result = service.wxLogin(request());
            assertThat(result.getId()).isEqualTo(88L); assertThat(result.getOpenid()).isEqualTo("new-openid");
            assertThat(result.getCreateTime()).isBetween(before,LocalDateTime.now());
        }
        verify(mapper).save(user.capture()); assertThat(user.getValue().getOpenid()).isEqualTo("new-openid");
    }
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", "   ", "\t\n", "{}", "null", "[]", "[\"openid-7\"]", "123", "true", "\"openid-7\"",
            "{\"errcode\":40029,\"errmsg\":\"invalid code\"}",
            "{\"openid\":null}", "{\"openid\":\"\"}", "{\"openid\":\"   \"}",
            "{\"openid\":123}", "{\"openid\":true}", "{\"openid\":[]}", "{\"openid\":{}}",
            "{\"openid\":", "<html>upstream error</html>",
            "{\"openid\":\"openid-7\"} {}", "{\"openid\":\"openid-7\"} trailing"
    })
    void invalidWechatResponseRejectsLoginWithoutReadingOrWritingUser(String body) {
        try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
            http.when(() -> HttpClientUtil.doGet(anyString(),anyMap())).thenReturn(body);
            assertThatThrownBy(() -> service.wxLogin(request())).isInstanceOf(LoginFailedException.class).hasMessage(MessageConstant.LOGIN_FAILED);
        }
        verifyNoInteractions(mapper);
    }
}
