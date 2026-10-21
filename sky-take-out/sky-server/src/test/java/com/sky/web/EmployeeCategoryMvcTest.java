package com.sky.web;

import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.*;
import com.sky.result.PageResult;
import com.sky.support.*;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmployeeCategoryMvcTest extends WebTestSupport {
    @Test void loginIsPublicIssuesAdminJwtAndDoesNotReturnPassword() throws Exception {
        when(employeeService.login(any())).thenReturn(Employee.builder().id(7L).username("admin").name("管理员").password("secret-hash").build());
        String body = successful(post("/admin/employee/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.data.userName").value("admin")).andExpect(jsonPath("$.data.password").doesNotExist()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).at("/data/token").asText();
        assertThat(JwtUtil.parseJWT(WebTestConfiguration.ADMIN_SECRET,token).get("empId",Number.class).longValue()).isEqualTo(7L);
        verify(employeeService).login(argThat(dto -> dto.getUsername().equals("admin") && dto.getPassword().equals("123456")));
    }
    @Test void failedLoginUsesBusinessErrorEnvelope() throws Exception {
        when(employeeService.login(any())).thenThrow(new PasswordErrorException("密码错误"));
        mvc.perform(post("/admin/employee/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"bad\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("密码错误"));
    }
    @Test void passwordChangeAcceptsExistingFrontendBodyWithoutEmployeeId() throws Exception {
        successful(admin(put("/admin/employee/editPassword").contentType("application/json")
                .content("{\"oldPassword\":\"123456\",\"newPassword\":\"New123\"}")))
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(employeeService).editPassword(argThat(dto -> dto.getOldPassword().equals("123456")
                && dto.getNewPassword().equals("New123")));
    }
    @Test void passwordChangeIsProtectedByAdminAuthentication() throws Exception {
        mvc.perform(put("/admin/employee/editPassword").contentType("application/json")
                .content("{\"oldPassword\":\"123456\",\"newPassword\":\"New123\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(employeeService);
    }
    @Test void employeeCreateBindsProfile() throws Exception {
        successful(admin(post("/admin/employee").contentType("application/json").content("{\"username\":\"alice\",\"name\":\"员工\",\"phone\":\"13800000000\"}")));
        verify(employeeService).save(argThat(dto -> dto.getUsername().equals("alice") && dto.getName().equals("员工") && dto.getPhone().equals("13800000000")));
    }
    @Test void employeeUpdateBindsIdAndFields() throws Exception {
        successful(admin(put("/admin/employee").contentType("application/json").content("{\"id\":7,\"name\":\"新名字\"}")));
        verify(employeeService).update(argThat(dto -> dto.getId()==7L && dto.getName().equals("新名字")));
    }
    @Test void employeePageBindsQueryAndPreservesEnvelope() throws Exception {
        when(employeeService.pageQuery(any())).thenReturn(new PageResult(11,Collections.singletonList(Employee.builder().id(7L).name("员工").build())));
        successful(admin(get("/admin/employee/page").param("page","2").param("pageSize","5").param("name","员")))
                .andExpect(jsonPath("$.data.total").value(11)).andExpect(jsonPath("$.data.records[0].name").value("员工"));
        verify(employeeService).pageQuery(argThat(dto -> dto.getPage()==2 && dto.getPageSize()==5 && dto.getName().equals("员")));
    }
    @ParameterizedTest @CsvSource({"0,7","1,8"}) void employeeStatusBindsPathAndQuery(int state,long id) throws Exception {
        successful(admin(post("/admin/employee/status/"+state).param("id",String.valueOf(id)))); verify(employeeService).startOrStop(state,id);
    }
    @Test void employeeDetailAndLogout() throws Exception {
        when(employeeService.getById(7L)).thenReturn(Employee.builder().id(7L).password("****").build());
        successful(admin(get("/admin/employee/7"))).andExpect(jsonPath("$.data.password").value("****"));
        successful(admin(post("/admin/employee/logout")));
    }
    @Test void categoryCreateAndUpdateBindEditableFields() throws Exception {
        String body = "{\"id\":3,\"name\":\"热菜\",\"type\":1,\"sort\":2}";
        successful(admin(post("/admin/category").contentType("application/json").content(body)));
        successful(admin(put("/admin/category").contentType("application/json").content(body)));
        verify(categoryService).save(argThat(dto -> dto.getName().equals("热菜") && dto.getType()==1 && dto.getSort()==2));
        verify(categoryService).update(argThat(dto -> dto.getId()==3L));
    }
    @Test void categoryPageBindsFilters() throws Exception {
        when(categoryService.pageQuery(any())).thenReturn(new PageResult(0,Collections.emptyList()));
        successful(admin(get("/admin/category/page").param("page","1").param("pageSize","10").param("name","热").param("type","1")))
                .andExpect(jsonPath("$.data.records").isEmpty());
        verify(categoryService).pageQuery(argThat(dto -> dto.getType()==1 && dto.getName().equals("热") && dto.getPageSize()==10));
    }
    @Test void categoryDeleteAndStatusBindIds() throws Exception {
        successful(admin(delete("/admin/category").param("id","3"))); verify(categoryService).deleteById(3L);
        successful(admin(post("/admin/category/status/1").param("id","3"))); verify(categoryService).startOrStop(1,3L);
    }
    @Test void categoryListSupportsBothEndsAndOptionalType() throws Exception {
        when(categoryService.list(1)).thenReturn(Collections.singletonList(Category.builder().id(3L).name("热菜").build()));
        successful(admin(get("/admin/category/list").param("type","1"))).andExpect(jsonPath("$.data[0].name").value("热菜"));
        successful(user(get("/user/category/list").param("type","1"))).andExpect(jsonPath("$.data[0].id").value(3));
        when(categoryService.list(null)).thenReturn(Collections.emptyList()); successful(admin(get("/admin/category/list"))).andExpect(jsonPath("$.data").isEmpty());
    }
}
