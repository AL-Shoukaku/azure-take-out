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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=AzureApplication.class,properties={
        "spring.datasource.druid.url=jdbc:h2:mem:azure_employee_password;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeePasswordMvcIntegrationTest {
    private static final String OLD_HASH="e10adc3949ba59abbe56e057f20f883e";
    private static final String PATH="/admin/employee/editPassword";
    @Autowired private MockMvc mvc;
    @Autowired private JwtProperties jwt;
    @Autowired private EmployeeMapper employees;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordHasher passwordHasher;
    // 测试用：通过 MockitoBean 替换测试上下文中的组件，保持原有模拟范围。
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean(name="serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;
    @AfterEach void clearIdentity() { BaseContext.removeCurrentId(); }

    private MockHttpServletRequestBuilder request(String body,long employeeId) {
        String token=JwtUtil.createJWT(jwt.getAdminSecretKey(),60000,new HashMap<>(Collections.singletonMap("empId",employeeId)));
        return put(PATH).header(jwt.getAdminTokenName(),token).contentType("application/json").content(body);
    }
    private void unchangedPasswords() {
        assertThat(employees.getById(1L).getPassword()).isEqualTo(OLD_HASH);
        assertThat(employees.getById(2L).getPassword()).isEqualTo(OLD_HASH);
    }
    private void failed(String body,long employeeId,String message) throws Exception {
        mvc.perform(request(body,employeeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value(message));
        unchangedPasswords(); assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void legacyLoginUpgradesItsHashAndTheSamePasswordCanLogInAgain() throws Exception {
        assertThat(employees.getById(1L).getPassword()).isEqualTo(OLD_HASH);
        assertThat(BaseContext.getCurrentId()).isNull();

        mvc.perform(post("/admin/employee/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.password").doesNotExist());

        Employee upgraded = employees.getById(1L);
        String storedHash = upgraded.getPassword();
        assertThat(storedHash).startsWith("$argon2id$");
        assertThat(passwordHasher.matches("123456", storedHash)).isTrue();
        assertThat(upgraded.getUpdateUser()).isEqualTo(1L);
        assertThat(upgraded.getUpdateTime()).isNotNull();
        assertThat(employees.getById(2L).getPassword()).isEqualTo(OLD_HASH);
        assertThat(BaseContext.getCurrentId()).isNull();

        mvc.perform(post("/admin/employee/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        assertThat(employees.getById(1L).getPassword()).isEqualTo(storedHash);
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void frontendRequestChangesPasswordAndOnlyNewPasswordCanLogIn(boolean argon2id) throws Exception {
        if (argon2id) {
            jdbc.update("update employee set password = ? where id = ?", passwordHasher.encode("123456"), 1L);
        }
        Employee original=employees.getById(1L);
        mvc.perform(request("{\"oldPassword\":\"123456\",\"newPassword\":\"New123\",\"username\":\"hacked\",\"status\":0}",1L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertThat(BaseContext.getCurrentId()).isNull();
        Employee saved=employees.getById(1L);
        assertThat(saved.getPassword()).startsWith("$argon2id$");
        assertThat(passwordHasher.matches("New123", saved.getPassword())).isTrue();
        assertThat(saved.getPassword()).isNotEqualTo("New123");
        assertThat(saved.getUpdateUser()).isEqualTo(1L); assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(saved).extracting(Employee::getUsername,Employee::getName,Employee::getStatus,Employee::getPhone,Employee::getIdNumber)
                .containsExactly(original.getUsername(),original.getName(),original.getStatus(),original.getPhone(),original.getIdNumber());
        assertThat(employees.getById(2L).getPassword()).isEqualTo(OLD_HASH);
        mvc.perform(post("/admin/employee/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("密码错误"));
        mvc.perform(post("/admin/employee/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"New123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.token").isNotEmpty()).andExpect(jsonPath("$.data.password").doesNotExist());
    }
    @Test void unrecognizedEmployeeIdCannotRetargetPasswordChange() throws Exception {
        mvc.perform(request("{\"empId\":2,\"oldPassword\":\"123456\",\"newPassword\":\"New123\"}",1L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThat(passwordHasher.matches("New123",employees.getById(1L).getPassword())).isTrue();
        assertThat(employees.getById(2L).getPassword()).isEqualTo(OLD_HASH);
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void wrongOldPasswordDoesNotChangeEitherAccount() throws Exception {
        failed("{\"oldPassword\":\"654321\",\"newPassword\":\"New123\"}",1L,"密码错误");
    }
    @ParameterizedTest @ValueSource(strings={"abc12","A123456789012345678901","abc 123","abc$123"})
    void invalidNewPasswordReturnsBusinessErrorWithoutUpdating(String password) throws Exception {
        failed("{\"oldPassword\":\"123456\",\"newPassword\":\""+password+"\"}",1L,"新密码应为6-20位数字或字母");
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"oldPassword\":\"123456\"}","{\"newPassword\":\"New123\"}",
            "{\"oldPassword\":\" \",\"newPassword\":\"New123\"}","{\"oldPassword\":\"123456\",\"newPassword\":\"\"}"})
    void missingPasswordFieldsReturnBusinessError(String body) throws Exception {
        failed(body,1L,"密码修改失败");
    }
    @Test void disabledAccountCannotChangePasswordUsingAnExistingToken() throws Exception {
        failed("{\"oldPassword\":\"123456\",\"newPassword\":\"New123\"}",2L,"账号被锁定");
    }
    @Test void nonexistentEmployeeCannotChangePasswordUsingAnExistingToken() throws Exception {
        failed("{\"oldPassword\":\"123456\",\"newPassword\":\"New123\"}",999L,"账号不存在");
    }
    @ParameterizedTest @ValueSource(strings={"missing","malformed","expired","user"})
    void unauthorizedRequestsCannotChangePasswords(String kind) throws Exception {
        MockHttpServletRequestBuilder request=put(PATH).contentType("application/json")
                .content("{\"oldPassword\":\"123456\",\"newPassword\":\"New123\"}");
        if (kind.equals("malformed")) request.header(jwt.getAdminTokenName(),"invalid");
        if (kind.equals("expired")) request.header(jwt.getAdminTokenName(),JwtUtil.createJWT(jwt.getAdminSecretKey(),-60000,
                new HashMap<>(Collections.singletonMap("empId",1L))));
        if (kind.equals("user")) request.header(jwt.getAdminTokenName(),JwtUtil.createJWT(jwt.getUserSecretKey(),60000,
                new HashMap<>(Collections.singletonMap("userId",1L))));
        mvc.perform(request).andExpect(status().isUnauthorized()); unchangedPasswords();
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void malformedJsonIsRejectedBeforeChangingPasswords() throws Exception {
        mvc.perform(request("{",1L)).andExpect(status().isBadRequest()); unchangedPasswords();
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void passwordChangeUsesFullLongEmployeeIdAndDoesNotModifyItsTruncatedId() throws Exception {
        long largeId=(1L << 32)+1L;
        jdbc.update("insert into employee (id,name,username,password,phone,sex,id_number,status) values (?,?,?,?,?,?,?,?)",
                largeId,"大 ID 员工","large-employee",OLD_HASH,"13800000003","1","123456789012345680",1);
        mvc.perform(request("{\"oldPassword\":\"123456\",\"newPassword\":\"New123\"}",largeId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThat(employees.getById(largeId).getPassword()).startsWith("$argon2id$");
        assertThat(passwordHasher.matches("New123", employees.getById(largeId).getPassword())).isTrue();
        assertThat(employees.getById(largeId).getUpdateUser()).isEqualTo(largeId);
        unchangedPasswords();
    }
}
