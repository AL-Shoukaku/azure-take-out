package com.sky.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.SkyApplication;
import com.sky.task.OrderTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 测试用：加载实际 Boot 文档配置，隔离 Redis、定时任务和需要真实容器的 WebSocket 注册。 */
@SpringBootTest(classes = SkyApplication.class, properties = {
        "spring.datasource.druid.url=jdbc:h2:mem:sky_openapi_mvc;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE",
        "spring.cache.type=simple"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiMvcIntegrationTest {
    @Autowired private MockMvc mvc;
    // 测试用：通过 MockitoBean 替换测试上下文中的组件，保持原有模拟范围。
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean(name = "serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode document(String group) throws Exception {
        JsonNode doc = readJson("/v3/api-docs/" + group);
        assertThat(doc.path("openapi").asText()).startsWith("3.");
        return doc;
    }

    private JsonNode readJson(String path) throws Exception {
        byte[] body = mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode doc = json.readTree(body);
        // 错误的转换器顺序可能把 byte[] 文档转换成 Base64 字符串，不能只判断请求成功。
        assertThat(doc.isObject()).isTrue();
        return doc;
    }

    @Test
    void adminDocumentIsJsonAndContainsOnlyAdminEndpoints() throws Exception {
        JsonNode doc = document("admin");
        assertThat(doc.path("paths").has("/admin/employee/login")).isTrue();
        assertThat(doc.path("paths").has("/user/user/login")).isFalse();
        assertThat(doc.path("components").path("schemas").has("EmployeeLoginDTO")).isTrue();
        List<String> paths = new ArrayList<>();
        doc.path("paths").fieldNames().forEachRemaining(paths::add);
        assertThat(paths).isNotEmpty().allMatch(path -> path.startsWith("/admin/"));
    }

    @Test
    void userDocumentContainsOnlyUserEndpointsAndKeepsOperationAnnotations() throws Exception {
        JsonNode doc = document("user");
        assertThat(doc.path("paths").has("/user/user/login")).isTrue();
        assertThat(doc.path("paths").has("/admin/employee/login")).isFalse();
        List<String> paths = new ArrayList<>();
        doc.path("paths").fieldNames().forEachRemaining(paths::add);
        assertThat(paths).isNotEmpty().allMatch(path -> path.startsWith("/user/"));
        JsonNode submit = doc.path("paths").path("/user/order/submit").path("post");
        assertThat(submit.path("summary").asText()).isEqualTo("用户下单");
        assertThat(submit.path("tags").toString()).contains("用户端订单相关接口");
    }

    @Test
    void migratedSchemaAndOperationAnnotationsAppearInAdminDocument() throws Exception {
        JsonNode doc = document("admin");
        JsonNode schemas = doc.path("components").path("schemas");
        JsonNode login = schemas.path("EmployeeLoginDTO");
        assertThat(login.path("description").asText()).isEqualTo("员工登录时传递的数据模型");
        assertThat(login.path("properties").path("username").path("description").asText()).isEqualTo("用户名");
        assertThat(login.path("properties").path("password").path("description").asText()).isEqualTo("密码");
        assertThat(schemas.path("EmployeeLoginVO").path("properties").path("token")
                .path("description").asText()).isEqualTo("jwt令牌");
        JsonNode search = doc.path("paths").path("/admin/order/conditionSearch").path("get");
        assertThat(search.path("summary").asText()).isEqualTo("商家条件查询订单");
        assertThat(search.path("tags").toString()).contains("管理端订单相关接口");
    }

    @Test
    void userAccountSchemasReflectPasswordLoginAndSharedPasswordChangeDto() throws Exception {
        JsonNode doc = document("user");
        JsonNode paths = doc.path("paths");
        for (String path : List.of("/user/user/register", "/user/user/login", "/user/user/logout", "/user/common/upload")) {
            assertThat(paths.path(path).has("post")).as(path).isTrue();
        }
        assertThat(paths.path("/user/user/info").has("get")).isTrue();
        assertThat(paths.path("/user/user/update").has("put")).isTrue();
        assertThat(paths.path("/user/user/editPassword").has("put")).isTrue();
        JsonNode schemas = doc.path("components").path("schemas");
        assertThat(propertyNames(schemas.path("UserLoginDTO"))).containsExactlyInAnyOrder("account", "password");
        assertThat(propertyNames(schemas.path("UserRegisterDTO"))).containsExactlyInAnyOrder("account", "password");
        assertThat(propertyNames(schemas.path("UserLoginVO"))).containsExactlyInAnyOrder("id", "username", "avatar", "token");
        assertThat(propertyNames(schemas.path("UserInfoVO"))).contains("username").doesNotContain("password", "account");
        assertThat(propertyNames(schemas.path("UserInfoDTO"))).contains("id", "username", "avatar");
        assertThat(propertyNames(schemas.path("PasswordEditDTO"))).containsExactlyInAnyOrder("oldPassword", "newPassword");
        assertThat(propertyNames(document("admin").path("components").path("schemas").path("PasswordEditDTO")))
                .containsExactlyInAnyOrder("oldPassword", "newPassword");
    }

    private List<String> propertyNames(JsonNode schema) {
        List<String> names = new ArrayList<>();
        schema.path("properties").fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void documentUiConfigurationExposesBothGroups() throws Exception {
        JsonNode config = readJson("/v3/api-docs/swagger-config");
        List<String> urls = new ArrayList<>();
        config.path("urls").forEach(group -> urls.add(group.path("url").asText()));
        assertThat(urls).containsExactlyInAnyOrder("/v3/api-docs/admin", "/v3/api-docs/user");
    }

    @Test
    void knife4jPageAndItsScriptsRemainPublicAfterRemovingOldResourceMappings() throws Exception {
        String html = mvc.perform(get("/doc.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("<html", "id=app");
        // 从页面读取实际脚本路径，避免把带版本哈希的资源文件名写死在测试里。
        Matcher scripts = Pattern.compile("<script\\b[^>]*\\bsrc\\s*=\\s*[\"']?([^\\s\"'>]+)").matcher(html);
        List<String> paths = new ArrayList<>();
        while (scripts.find()) {
            paths.add("/" + scripts.group(1));
        }
        assertThat(paths).isNotEmpty();
        for (String path : paths) {
            try (var input = new ClassPathResource("META-INF/resources" + path).getInputStream()) {
                mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().bytes(input.readAllBytes()));
            }
        }
    }
}
