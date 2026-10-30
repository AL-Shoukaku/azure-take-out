package com.azure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.AzureApplication;
import com.azure.context.BaseContext;
import com.azure.entity.Orders;
import com.azure.mapper.OrdersMapper;
import com.azure.properties.JwtProperties;
import com.azure.task.OrderTask;
import com.azure.utils.JwtUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 测试用：验证完整 Boot MVC 自动配置与真实业务、SQL 的配合，不使用独立 MVC 测试配置。 */
@SpringBootTest(classes = AzureApplication.class, properties = {
        "spring.datasource.druid.url=jdbc:h2:mem:azure_boot_mvc;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE",
        "spring.cache.type=simple"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BootMvcIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtProperties jwt;
    @Autowired private OrdersMapper orders;
    // 测试用：通过 MockitoBean 替换测试上下文中的组件，保持原有模拟范围。
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean private RedissonClient redissonClient;
    @MockitoBean(name = "serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;

    private final ObjectMapper json = new ObjectMapper();
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    // 测试用：包含中文和空格的临时目录，同时验证 file URI 路径转义，不写入真实上传目录。
    private static final Path UPLOADS = createUploadDirectory();

    private static Path createUploadDirectory() {
        try {
            return Files.createTempDirectory("azure-boot-mvc-图片 ");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("azure.upload-path", UPLOADS::toString);
    }

    @AfterEach
    void clearIdentity() {
        BaseContext.removeCurrentId();
    }

    @AfterAll
    static void deleteUploads() throws IOException {
        try (var files = Files.walk(UPLOADS)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header(jwt.getAdminTokenName(),
                JwtUtil.createJWT(jwt.getAdminSecretKey(), 60000, new HashMap<>(Map.of("empId", 1L))));
    }

    private MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header(jwt.getUserTokenName(),
                JwtUtil.createJWT(jwt.getUserSecretKey(), 60000, new HashMap<>(Map.of("userId", 42L))));
    }

    @Test
    void adminDateFiltersAndPageHelperWorkThroughBootAutoConfiguration() throws Exception {
        // 两笔已完成订单在日期范围内，实际 PageHelper 应每页只返回一笔，而不是仅伪造总数。
        for (int page = 1; page <= 2; page++) {
            long expectedId = page == 1 ? 3L : 2L;
            Orders expected = orders.getById(expectedId);
            mvc.perform(admin(get("/admin/order/conditionSearch")
                            .param("page", String.valueOf(page)).param("pageSize", "1").param("status", "5")
                            .param("beginTime", "2026-10-02 00:00:00").param("endTime", "2026-10-03 23:59:59")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.records.length()").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(expectedId))
                    .andExpect(jsonPath("$.data.records[0].orderTime").value(expected.getOrderTime().format(MINUTES)));
            assertThat(BaseContext.getCurrentId()).isNull();
        }
    }

    @Test
    void scheduledDeliveryKeepsSecondsInDatabaseAndOrderResponseKeepsMinuteFormat() throws Exception {
        LocalDateTime delivery = LocalDateTime.now().plusHours(2).withSecond(45).withNano(0);
        String body = """
                {"addressBookId":1,"payMethod":1,"remark":"少盐","amount":42.00,"packAmount":3,
                 "deliveryStatus":0,"estimatedDeliveryTime":"%s","tablewareStatus":0,"tablewareNumber":2,
                 "futureField":true}
                """.formatted(delivery.format(SECONDS));
        byte[] response = mvc.perform(user(post("/user/order/submit").contentType("application/json").content(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.orderAmount").value(42.0))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode envelope = json.readTree(response);
        assertThat(envelope.has("msg")).isTrue();
        assertThat(envelope.get("msg").isNull()).isTrue();
        JsonNode result = envelope.path("data");
        assertThat(result.path("id").isIntegralNumber()).isTrue();
        assertThat(result.path("orderAmount").isNumber()).isTrue();
        Orders saved = orders.getById(result.path("id").asLong());
        assertThat(saved.getEstimatedDeliveryTime()).isEqualTo(delivery);
        assertThat(saved.getRemark()).isEqualTo("少盐");
        assertThat(result.path("orderTime").asText()).isEqualTo(saved.getOrderTime().format(MINUTES));
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void uploadedImageCanBeReadWithoutLoginThroughBootResourceHandling() throws Exception {
        // 测试用：有效的 1×1 PNG，上传及读取均走实际 Controller 和资源映射。
        byte[] image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        byte[] response = mvc.perform(admin(multipart("/admin/common/upload")
                        .file(new MockMultipartFile("file", "菜品.png", "image/png", image))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsByteArray();
        String path = URI.create(json.readTree(response).path("data").asText()).getPath();
        assertThat(Files.readAllBytes(UPLOADS.resolve("admin").resolve(Path.of(path).getFileName()))).isEqualTo(image);
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png")).andExpect(content().bytes(image));
    }

    @Test
    void missingAdminOrderReturnsTheExistingBusinessErrorEnvelope() throws Exception {
        byte[] response = mvc.perform(admin(get("/admin/order/details/999")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("订单不存在"))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode envelope = json.readTree(response);
        assertThat(envelope.has("data")).isTrue();
        assertThat(envelope.get("data").isNull()).isTrue();
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"turnoverStatistics", "userStatistics", "ordersStatistics", "top10"})
    void missingOrReversedReportDatesReturnBusinessErrorsInsteadOfServerErrors(String endpoint) throws Exception {
        String path = "/admin/report/" + endpoint;
        // 测试用：实际请求可漏传日期或传入反向区间；沿用现有 HTTP 200 + code=0 的业务规则。
        for (MockHttpServletRequestBuilder request : List.of(
                get(path).param("end", "2026-10-03"),
                get(path).param("begin", "2026-10-02"),
                get(path).param("begin", "2026-10-03").param("end", "2026-10-02"))) {
            mvc.perform(admin(request)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.msg").value("日期不能为空，且开始日期不能晚于结束日期"));
            assertThat(BaseContext.getCurrentId()).isNull();
        }
    }

    @Test
    void actualReportExportRemainsReadableAsExcelThroughBootMvc() throws Exception {
        byte[] response = mvc.perform(admin(get("/admin/report/export")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).startsWith("时间:");
            LocalDateTime first = LocalDateTime.parse(sheet.getRow(7).getCell(1).getStringCellValue());
            for (int row = 7; row < 37; row++) {
                assertThat(sheet.getRow(row).getCell(1).getStringCellValue())
                        .isEqualTo(first.plusDays(row - 7).toString());
            }
        }
        assertThat(BaseContext.getCurrentId()).isNull();
    }
}
