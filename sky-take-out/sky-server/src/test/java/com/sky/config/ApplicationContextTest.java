package com.sky.config;

import com.sky.SkyApplication;
import com.sky.context.BaseContext;
import com.sky.mapper.OrdersMapper;
import com.sky.service.*;
import com.sky.task.OrderTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=SkyApplication.class, properties={
        // 测试用：采用非默认端口和库号，防止配置迁移失效却被默认值掩盖。
        "sky.redis.port=16379", "sky.redis.database=6"})
@ActiveProfiles("test")
class ApplicationContextTest {
    @Autowired private ApplicationContext context;
    @Autowired private OrdersMapper ordersMapper;
    @MockBean private LettuceConnectionFactory redisConnectionFactory;
    @MockBean(name="serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockBean private OrderTask orderTask;
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
    @org.springframework.transaction.annotation.Transactional
    void employeeCreationUsesActualMapperAuditAspectAndDatabaseDefault() {
        BaseContext.setCurrentId(42L);
        com.sky.dto.EmployeeDTO request = new com.sky.dto.EmployeeDTO();
        request.setUsername("audit-test"); request.setName("测试员工"); request.setPhone("13800000002");
        request.setSex("1"); request.setIdNumber("123456789012345670");
        context.getBean(EmployeeService.class).save(request);
        com.sky.entity.Employee saved = context.getBean(com.sky.mapper.EmployeeMapper.class).getByUsername("audit-test");
        assertThat(saved.getCreateUser()).isEqualTo(42L);
        assertThat(saved.getUpdateUser()).isEqualTo(42L);
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isEqualTo(saved.getCreateTime());
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getPassword()).isEqualTo("e10adc3949ba59abbe56e057f20f883e");
    }
}
