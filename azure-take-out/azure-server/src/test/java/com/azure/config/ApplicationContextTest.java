package com.azure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.AzureApplication;
import com.azure.constant.PasswordConstant;
import com.azure.context.BaseContext;
import com.azure.mapper.OrdersMapper;
import com.azure.security.PasswordHasher;
import com.azure.service.*;
import com.azure.task.OrderTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=AzureApplication.class, properties={
        // 测试用：采用非默认端口和库号，防止配置迁移失效却被默认值掩盖。
        "azure.redis.port=16379", "azure.redis.database=6"})
@ActiveProfiles("test")
class ApplicationContextTest {
    @Autowired private ApplicationContext context;
    @Autowired private OrdersMapper ordersMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RequestMappingHandlerAdapter handlerAdapter;
    // 测试用：通过 MockitoBean 替换测试上下文中的组件，保持原有模拟范围。
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean(name="serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;
    @AfterEach void clearIdentity() { BaseContext.removeCurrentId(); }
    @Test void productionApplicationWiresAllServicesAndUsesIsolatedDatabase() {
        for (Class<?> type : new Class<?>[]{EmployeeService.class,CategoryService.class,DishService.class,SetmealService.class,
                OrdersService.class,AddressBookService.class,ShoppingCartService.class,UserService.class,ReportService.class,WorkspaceService.class}) {
            assertThat(context.getBean(type)).isNotNull();
        }
        assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("test");
        assertThat(context.getEnvironment().getProperty("spring.datasource.druid.url")).startsWith("jdbc:h2:mem:");
        assertThat(ordersMapper.countByStatus(null)).isEqualTo(4);
    }

    @Test
    void migratedRedisPropertiesBindHostPortAndDatabaseWithoutConnecting() {
        RedisProperties redis = context.getBean(RedisProperties.class);
        assertThat(redis.getHost()).isEqualTo("unused.invalid");
        assertThat(redis.getPort()).isEqualTo(16379);
        assertThat(redis.getDatabase()).isEqualTo(6);
    }

    @Test
    void mvcJsonConvertersUseTheSharedApplicationObjectMapper() {
        assertThat(context.getBeanNamesForType(ObjectMapper.class)).containsExactly("objectMapper");
        assertThat(context.getBean("objectMapper")).isSameAs(objectMapper);
        var converters = handlerAdapter.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .toList();
        assertThat(converters).isNotEmpty().allSatisfy(converter ->
                assertThat(converter.getObjectMapper()).isSameAs(objectMapper));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void employeeCreationUsesActualMapperAuditAspectAndDatabaseDefault() {
        BaseContext.setCurrentId(1L);
        com.azure.dto.EmployeeDTO request = new com.azure.dto.EmployeeDTO();
        request.setUsername("audit-test"); request.setName("测试员工"); request.setPhone("13800000002");
        request.setSex("1"); request.setIdNumber("123456789012345670");
        context.getBean(EmployeeService.class).save(request);
        com.azure.entity.Employee saved = context.getBean(com.azure.mapper.EmployeeMapper.class).getByUsername("audit-test");
        assertThat(saved.getCreateUser()).isEqualTo(1L);
        assertThat(saved.getUpdateUser()).isEqualTo(1L);
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isEqualTo(saved.getCreateTime());
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getPassword()).startsWith("$argon2id$");
        assertThat(context.getBean(PasswordHasher.class)
                .matches(PasswordConstant.DEFAULT_PASSWORD, saved.getPassword())).isTrue();
    }
}
