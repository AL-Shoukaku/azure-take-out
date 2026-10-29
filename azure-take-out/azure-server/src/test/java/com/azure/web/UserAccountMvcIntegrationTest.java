package com.azure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.AzureApplication;
import com.azure.constant.MessageConstant;
import com.azure.context.BaseContext;
import com.azure.entity.User;
import com.azure.mapper.UserMapper;
import com.azure.properties.JwtProperties;
import com.azure.security.PasswordHasher;
import com.azure.support.ImageFixtures;
import com.azure.task.OrderTask;
import com.azure.utils.JwtUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Controller、Service、密码散列、JWT 和 MyBatis，数据库逐例回滚，上传只写临时目录。 */
@SpringBootTest(classes = AzureApplication.class, properties = {
        "spring.datasource.druid.url=jdbc:h2:mem:azure_user_account;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE",
        "spring.cache.type=simple"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAccountMvcIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtProperties jwt;
    @Autowired private UserMapper users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordHasher passwordHasher;
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean private RedissonClient redissonClient;
    @MockitoBean(name = "serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;

    private static final Path UPLOADS = uploadDirectory();

    private static Path uploadDirectory() {
        try {
            return Files.createTempDirectory("azure-user-account-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("azure.upload-path", UPLOADS::toString);
    }

    @AfterEach void cleanup() throws IOException {
        BaseContext.removeCurrentId();
        ImageFixtures.clearDirectory(UPLOADS);
    }

    @AfterAll static void removeUploadDirectory() throws IOException {
        Files.delete(UPLOADS);
    }

    private MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header(jwt.getUserTokenName(),
                JwtUtil.createJWT(jwt.getUserSecretKey(), 60000, Map.of("userId", 42L)));
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object payload) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(payload));
    }

    private JsonNode success(MockHttpServletRequestBuilder request) throws Exception {
        byte[] response = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1)).andReturn().getResponse().getContentAsByteArray();
        assertThat(BaseContext.getCurrentId()).isNull();
        return json.readTree(response).path("data");
    }

    private void failure(MockHttpServletRequestBuilder request, String message) throws Exception {
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(message));
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test void registrationLoginAndCurrentProfileWorkTogether() throws Exception {
        success(body(post("/user/user/register"), Map.of("account", "new-account", "password", "123456")));
        User saved = users.getByAccount("new-account");
        assertThat(saved.getPassword()).startsWith("$argon2id$").isNotEqualTo("123456");
        assertThat(passwordHasher.matches("123456", saved.getPassword())).isTrue();
        assertThat(saved.getUsername()).startsWith("用户");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getAvatar()).isNull();
        assertThat(saved.getUpdateTime()).isEqualTo(saved.getCreateTime());

        JsonNode login = success(body(post("/user/user/login"), Map.of("account", "new-account", "password", "123456")));
        var fields = new ArrayList<String>();
        login.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "username", "avatar", "token");
        assertThat(login.path("id").asLong()).isEqualTo(saved.getId());
        String token = login.path("token").asText();
        assertThat(JwtUtil.parseJWT(jwt.getUserSecretKey(), token).get("userId", Long.class)).isEqualTo(saved.getId());
        JsonNode info = success(get("/user/user/info").header(jwt.getUserTokenName(), token));
        assertThat(info.path("id").asLong()).isEqualTo(saved.getId());
        assertThat(info.path("account").asText()).isEqualTo(saved.getAccount());
        assertThat(info.path("username").asText()).isEqualTo(saved.getUsername());
        assertThat(info.has("password")).isFalse();
    }

    @Test void duplicateAccountDoesNotReplaceAnExistingUser() throws Exception {
        User original = users.getById(42L);
        failure(body(post("/user/user/register"), Map.of("account", "user42", "password", "654321")), "账号已存在");
        assertThat(users.getById(42L)).usingRecursiveComparison().isEqualTo(original);
        assertThat(jdbc.queryForObject("select count(*) from user", Integer.class)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"account\":\"new\"}", "{\"password\":\"123456\"}",
            "{\"account\":\" \",\"password\":\"123456\"}", "{\"account\":\"new\",\"password\":\" \"}"})
    void blankCredentialsFailBeforeCreatingUsersOrTokens(String payload) throws Exception {
        for (String endpoint : new String[]{"register", "login"}) {
            failure(post("/user/user/" + endpoint).contentType("application/json").content(payload), "账号和密码不能为空");
        }
        assertThat(jdbc.queryForObject("select count(*) from user", Integer.class)).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"missing,123456,账号不存在,1", "user42,wrong,密码错误,1", "user42,123456,账号被锁定,0"})
    void loginRejectsMissingAccountWrongPasswordAndDisabledAccount(String account, String password, String message, int status) throws Exception {
        jdbc.update("update user set status = ? where id = 42", status);
        failure(body(post("/user/user/login"), Map.of("account", account, "password", password)), message);
    }

    @Test void currentProfileUsesTokenIdentityRatherThanQueryParameter() throws Exception {
        JsonNode info = success(user(get("/user/user/info").param("id", "43")));
        assertThat(info.path("id").asLong()).isEqualTo(42L);
        assertThat(info.path("account").asText()).isEqualTo("user42");
        assertThat(info.path("username").asText()).isEqualTo("测试昵称");
        assertThat(info.path("phone").asText()).isEqualTo("13800000042");
        mvc.perform(user(get("/user/user/info/43"))).andExpect(status().isNotFound());
    }

    @Test void profileUpdateAllowsSharedNicknamesAndCannotChangeCredentialsOrStatus() throws Exception {
        User original = users.getById(42L);
        User other = users.getById(43L);
        success(body(user(put("/user/user/update")), Map.of("id", 42L, "username", other.getUsername(),
                "name", "新姓名", "phone", "13900000042", "account", "hacked", "password", "hacked", "status", 0)));
        User saved = users.getById(42L);
        assertThat(saved.getUsername()).isEqualTo(other.getUsername());
        assertThat(saved.getName()).isEqualTo("新姓名");
        assertThat(saved.getPhone()).isEqualTo("13900000042");
        assertThat(saved.getUpdateTime()).isAfter(original.getUpdateTime());
        assertThat(saved).extracting(User::getAccount, User::getPassword, User::getStatus, User::getCreateTime)
                .containsExactly(original.getAccount(), original.getPassword(), original.getStatus(), original.getCreateTime());
        assertThat(users.getById(43L)).usingRecursiveComparison().isEqualTo(other);
        assertThat(success(user(get("/user/user/info"))).path("username").asText()).isEqualTo(other.getUsername());
    }

    @Test void foreignProfileCannotBeUpdated() throws Exception {
        User other = users.getById(43L);
        failure(body(user(put("/user/user/update")), Map.of("id", 43L, "username", "冒用昵称")), "不能修改其他用户信息");
        assertThat(users.getById(43L)).usingRecursiveComparison().isEqualTo(other);
    }

    @Test void passwordChangeUsesTokenIdentityAndPreservesProfile() throws Exception {
        User original = users.getById(42L);
        User other = users.getById(43L);
        String nextPassword = " New密码123 ";
        success(body(user(put("/user/user/editPassword")), Map.of("oldPassword", "123456", "newPassword", nextPassword,
                "id", 43L, "empId", 43L)));
        User saved = users.getById(42L);
        assertThat(saved.getPassword()).startsWith("$argon2id$");
        assertThat(passwordHasher.matches(nextPassword, saved.getPassword())).isTrue();
        assertThat(saved.getUpdateTime()).isAfter(original.getUpdateTime());
        assertThat(saved).usingRecursiveComparison().ignoringFields("password", "updateTime").isEqualTo(original);
        assertThat(users.getById(43L)).usingRecursiveComparison().isEqualTo(other);
        failure(body(post("/user/user/login"), Map.of("account", "user42", "password", "123456")), MessageConstant.PASSWORD_ERROR);
        success(body(post("/user/user/login"), Map.of("account", "user42", "password", nextPassword)));
    }

    @Test void incorrectOrMissingOldPasswordDoesNotChangeHash() throws Exception {
        String original = users.getById(42L).getPassword();
        failure(body(user(put("/user/user/editPassword")), Map.of("oldPassword", "wrong", "newPassword", "654321")), MessageConstant.OLD_PASSWORD_ERROR);
        failure(body(user(put("/user/user/editPassword")), Map.of("newPassword", "654321")), MessageConstant.INPUT_CAN_NOT_BE_EMPTY);
        assertThat(users.getById(42L).getPassword()).isEqualTo(original);
    }

    @Test void avatarUploadAndProfileSaveAreSeparateSteps() throws Exception {
        byte[] image = ImageFixtures.png();
        User original = users.getById(42L);
        String url = success(user(multipart("/user/common/upload")
                .file(new MockMultipartFile("file", "avatar.png", "image/png", image)))).asText();
        String path = URI.create(url).getPath();
        assertThat(path).startsWith("/images/user/");
        assertThat(Files.readAllBytes(UPLOADS.resolve("user").resolve(Path.of(path).getFileName()))).isEqualTo(image);
        assertThat(users.getById(42L).getAvatar()).isEqualTo(original.getAvatar());
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().bytes(image));
        success(body(user(put("/user/user/update")), Map.of("id", 42L, "avatar", url)));
        assertThat(success(user(get("/user/user/info"))).path("avatar").asText()).isEqualTo(url);
        assertThat(users.getById(43L).getAvatar()).isNull();
    }

    @Test void logoutSucceedsAndTokenValidityFollowsCurrentClientManagedDesign() throws Exception {
        String token = success(body(post("/user/user/login"), Map.of("account", "user42", "password", "123456")))
                .path("token").asText();
        success(post("/user/user/logout").header(jwt.getUserTokenName(), token));
        success(get("/user/user/info").header(jwt.getUserTokenName(), token));
    }
}
