package com.azure.web;

import com.azure.AzureApplication;
import com.azure.context.BaseContext;
import com.azure.entity.Employee;
import com.azure.mapper.EmployeeMapper;
import com.azure.properties.JwtProperties;
import com.azure.security.PasswordHasher;
import com.azure.task.OrderTask;
import com.azure.utils.JwtUtil;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AzureApplication.class, properties = {
        "spring.datasource.druid.url=jdbc:h2:mem:azure_employee_permission;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeePermissionMvcIntegrationTest {
    private static final String PATH = "/admin/employee";
    private static final String NEW_EMPLOYEE = """
            {"username":"newstaff","name":"新员工","phone":"13800000003","sex":"1","idNumber":"11010119900101123X"}
            """;
    @Autowired private MockMvc mvc;
    @Autowired private JwtProperties jwt;
    @Autowired private EmployeeMapper employees;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordHasher passwordHasher;
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean private RedissonClient redissonClient;
    @MockitoBean(name = "serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;

    @AfterEach void clearIdentity() { BaseContext.removeCurrentId(); }

    private MockHttpServletRequestBuilder asEmployee(MockHttpServletRequestBuilder request, long id) {
        return request.header(jwt.getAdminTokenName(), JwtUtil.createJWT(jwt.getAdminSecretKey(),
                60000, new HashMap<>(Map.of("empId", id))));
    }

    @Test void adminCanCreateEmployeeWithEnabledStatusAndDefaultPassword() throws Exception {
        mvc.perform(asEmployee(post(PATH).contentType("application/json").content(NEW_EMPLOYEE), 1L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        Employee saved = employees.getByUsername("newstaff");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getCreateUser()).isEqualTo(1L);
        assertThat(passwordHasher.matches("123456", saved.getPassword())).isTrue();
    }

    @Test void ordinaryEmployeeCannotCreateEmployeeByCallingApiDirectly() throws Exception {
        jdbc.update("update employee set status = 1 where id = 2");
        mvc.perform(asEmployee(post(PATH).contentType("application/json").content(NEW_EMPLOYEE), 2L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("只有管理员可以操作"));
        assertThat(employees.getByUsername("newstaff")).isNull();
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void adminChangesOnlyTargetStatusWithoutCopyingItsOwnCredentials(int state) throws Exception {
        jdbc.update("update employee set status = ?, password = ? where id = 2",
                1 - state, passwordHasher.encode("Staff123"));
        Employee target = employees.getById(2L);
        Employee admin = employees.getById(1L);
        mvc.perform(asEmployee(post(PATH + "/status/" + state).param("id", "2"), 1L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        Employee saved = employees.getById(2L);
        assertThat(saved.getStatus()).isEqualTo(state);
        assertThat(saved.getUpdateUser()).isEqualTo(1L);
        assertThat(saved).usingRecursiveComparison().ignoringFields("status", "updateUser", "updateTime")
                .isEqualTo(target);
        assertThat(employees.getById(1L)).usingRecursiveComparison().isEqualTo(admin);
    }

    @ParameterizedTest @CsvSource({"0,1", "0,2", "1,1", "1,2"})
    void ordinaryEmployeeCannotChangeAnyEmployeeStatus(int state, long targetId) throws Exception {
        jdbc.update("update employee set status = 1 where id = 2");
        mvc.perform(asEmployee(post(PATH + "/status/" + state).param("id", String.valueOf(targetId)), 2L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("只有管理员可以操作"));
        assertThat(employees.getById(targetId).getStatus()).isEqualTo(1);
        assertThat(employees.getById(targetId).getUpdateTime()).isNull();
    }

    @Test void adminCannotDisableOwnAccount() throws Exception {
        mvc.perform(asEmployee(post(PATH + "/status/0").param("id", "1"), 1L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("不能禁用自己的账号"));
        assertThat(employees.getById(1L).getStatus()).isEqualTo(1);
    }

    @Test void disabledAdminCannotUseAnExistingTokenToManageEmployees() throws Exception {
        jdbc.update("update employee set status = 0 where id = 1");
        mvc.perform(asEmployee(post(PATH).contentType("application/json").content(NEW_EMPLOYEE), 1L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(asEmployee(post(PATH + "/status/1").param("id", "2"), 1L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertThat(employees.getByUsername("newstaff")).isNull();
        assertThat(employees.getById(2L).getStatus()).isZero();
    }

    @Test void employeeCannotRenameAdminToTakeItsPrivilegedUsername() throws Exception {
        jdbc.update("update employee set status = 1 where id = 2");
        mvc.perform(asEmployee(put(PATH).contentType("application/json")
                        .content("{\"id\":1,\"username\":\"formeradmin\"}"), 2L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("管理员账号不可修改"));
        assertThat(employees.getById(1L).getUsername()).isEqualTo("admin");
    }

    @Test void ordinaryEmployeeCanStillEditItsProfileWithoutChangingStatusOrPassword() throws Exception {
        jdbc.update("update employee set status = 1 where id = 2");
        Employee original = employees.getById(2L);
        mvc.perform(asEmployee(put(PATH).contentType("application/json")
                        .content("{\"id\":2,\"username\":\"staff2\",\"name\":\"新名字\",\"status\":0,\"password\":\"hacked\"}"), 2L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        Employee saved = employees.getById(2L);
        assertThat(saved.getUsername()).isEqualTo("staff2");
        assertThat(saved.getName()).isEqualTo("新名字");
        assertThat(saved.getStatus()).isEqualTo(original.getStatus());
        assertThat(saved.getPassword()).isEqualTo(original.getPassword());
    }
}
