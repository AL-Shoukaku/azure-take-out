package com.azure.service.impl;

import com.github.pagehelper.PageHelper;
import com.azure.constant.MessageConstant;
import com.azure.context.BaseContext;
import com.azure.dto.*;
import com.azure.entity.Employee;
import com.azure.exception.*;
import com.azure.mapper.EmployeeMapper;
import com.azure.result.PageResult;
import com.azure.security.PasswordHasher;
import com.azure.support.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeServiceImplTest extends ServiceTestSupport {
    @InjectMocks private EmployeeServiceImpl service;
    @Mock private EmployeeMapper mapper;
    @Spy private PasswordHasher passwordHasher = new PasswordHasher();
    @Captor private ArgumentCaptor<Employee> employee;

    private EmployeeLoginDTO login() {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername("admin"); dto.setPassword("123456"); return dto;
    }
    private Employee account(int status) {
        return Employee.builder().id(7L).username("admin").status(status)
                .password("e10adc3949ba59abbe56e057f20f883e").build();
    }
    @Test void loginReturnsEnabledAccount() {
        Employee expected = account(1);
        when(mapper.getByUsername("admin")).thenReturn(expected);
        assertThat(service.login(login())).isSameAs(expected);
    }
    @Test void loginRejectsUnknownAccount() {
        assertThatThrownBy(() -> service.login(login())).isInstanceOf(AccountNotFoundException.class)
                .hasMessage(MessageConstant.ACCOUNT_NOT_FOUND);
    }
    @Test void loginRejectsWrongPassword() {
        when(mapper.getByUsername("admin")).thenReturn(account(1));
        EmployeeLoginDTO dto = login(); dto.setPassword("incorrect");
        assertThatThrownBy(() -> service.login(dto)).isInstanceOf(PasswordErrorException.class)
                .hasMessage(MessageConstant.PASSWORD_ERROR);
    }
    @Test void loginRejectsDisabledAccount() {
        when(mapper.getByUsername("admin")).thenReturn(account(0));
        assertThatThrownBy(() -> service.login(login())).isInstanceOf(AccountLockedException.class)
                .hasMessage(MessageConstant.ACCOUNT_LOCKED);
        verify(mapper,never()).update(any());
    }
    @Test void failedLoginUpgradeClearsItsTemporaryEmployeeIdentity() {
        BaseContext.removeCurrentId();
        when(mapper.getByUsername("admin")).thenReturn(account(1));
        when(mapper.update(any())).thenThrow(new IllegalStateException("injected update failure"));

        assertThatThrownBy(() -> service.login(login()))
                .isInstanceOf(IllegalStateException.class).hasMessage("injected update failure");
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void saveCopiesProfileAndSetsEnabledDefaultPassword() {
        Employee admin = account(1); admin.setId(USER_ID);
        when(mapper.getById(USER_ID)).thenReturn(admin);
        EmployeeDTO dto = new EmployeeDTO(); dto.setName("员工"); dto.setUsername("new-user");
        dto.setPhone("13800000000"); dto.setSex("1"); dto.setIdNumber("123456789");
        service.save(dto);
        verify(mapper).insert(employee.capture());
        assertThat(employee.getValue()).extracting(Employee::getName, Employee::getUsername,
                Employee::getPhone, Employee::getSex, Employee::getIdNumber, Employee::getStatus)
                .containsExactly("员工", "new-user", "13800000000", "1", "123456789", 1);
        assertThat(employee.getValue().getPassword()).startsWith("$argon2id$");
        assertThat(passwordHasher.matches("123456", employee.getValue().getPassword())).isTrue();
    }
    @Test void pagePreservesTotalAndPaginationRequest() {
        EmployeePageQueryDTO dto = new EmployeePageQueryDTO(); dto.setPage(2); dto.setPageSize(5); dto.setName("员");
        Employee expected = account(1);
        when(mapper.pageQuery(dto)).thenAnswer(invocation -> {
            assertThat(PageHelper.getLocalPage().getPageNum()).isEqualTo(2);
            assertThat(PageHelper.getLocalPage().getPageSize()).isEqualTo(5);
            return page(12, expected);
        });
        PageResult result = service.pageQuery(dto);
        assertThat(result.getTotal()).isEqualTo(12);
        assertThat(result.getRecords()).containsExactly(expected);
        assertThat(expected.getPassword()).isEqualTo("****");
    }
    @ParameterizedTest @ValueSource(ints = {0, 1})
    void statusChangeUpdatesOnlyTargetAndStatus(int status) {
        Employee admin = account(1); admin.setId(USER_ID);
        when(mapper.getById(USER_ID)).thenReturn(admin);
        service.startOrStop(status, 7L);
        verify(mapper).update(employee.capture());
        assertThat(employee.getValue().getId()).isEqualTo(7L);
        assertThat(employee.getValue().getStatus()).isEqualTo(status);
        assertThat(employee.getValue().getPassword()).isNull();
        assertThat(employee.getValue().getUsername()).isNull();
        assertThat(admin.getId()).isEqualTo(USER_ID);
    }
    @Test void detailMasksPassword() {
        when(mapper.getById(7L)).thenReturn(account(1));
        assertThat(service.getById(7L).getPassword()).isEqualTo("****");
    }
    @Test void updateDoesNotResetPasswordOrStatus() {
        when(mapper.getById(7L)).thenReturn(Employee.builder().id(7L).username("staff7").build());
        EmployeeDTO dto = new EmployeeDTO(); dto.setId(7L); dto.setName("新名字"); dto.setPhone("13900000000");
        service.update(dto); verify(mapper).update(employee.capture());
        assertThat(employee.getValue().getId()).isEqualTo(7L);
        assertThat(employee.getValue().getName()).isEqualTo("新名字");
        assertThat(employee.getValue().getPhone()).isEqualTo("13900000000");
        assertThat(employee.getValue().getPassword()).isNull();
        assertThat(employee.getValue().getStatus()).isNull();
    }
}
