package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.*;
import com.sky.mapper.EmployeeMapper;
import com.sky.support.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.springframework.util.DigestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeePasswordServiceTest extends ServiceTestSupport {
    private static final String OLD_HASH="e10adc3949ba59abbe56e057f20f883e";
    @InjectMocks private EmployeeServiceImpl service;
    @Mock private EmployeeMapper mapper;
    @Captor private ArgumentCaptor<Employee> update;

    private PasswordEditDTO request() {
        PasswordEditDTO dto=new PasswordEditDTO(); dto.setOldPassword("123456"); dto.setNewPassword("New123"); return dto;
    }
    private Employee current(Integer status) {
        return Employee.builder().id(BaseContext.getCurrentId()).username("admin").status(status).password(OLD_HASH).build();
    }
    @ParameterizedTest @ValueSource(strings={"New123","123456","abcdef","Ab123456789012345678"})
    void validPasswordUpdatesOnlyCurrentEmployeesPassword(String password) {
        when(mapper.getById(USER_ID)).thenReturn(current(1)); when(mapper.updatePassword(any(),eq(OLD_HASH))).thenReturn(1);
        PasswordEditDTO dto=request(); dto.setNewPassword(password); service.editPassword(dto);
        verify(mapper).updatePassword(update.capture(),eq(OLD_HASH));
        assertThat(update.getValue().getId()).isEqualTo(USER_ID);
        assertThat(update.getValue().getPassword()).isEqualTo(DigestUtils.md5DigestAsHex(password.getBytes()));
        assertThat(update.getValue().getPassword()).isNotEqualTo(password);
        assertThat(update.getValue()).extracting(Employee::getName,Employee::getUsername,Employee::getPhone,Employee::getStatus)
                .containsOnlyNulls();
        verify(mapper,never()).update(any());
    }
    @ParameterizedTest @ValueSource(longs={42L,4294967338L})
    void optionalMatchingEmployeeIdUsesEntireAuthenticatedLongId(long empId) {
        BaseContext.setCurrentId(empId);
        when(mapper.getById(empId)).thenReturn(current(1)); when(mapper.updatePassword(any(),eq(OLD_HASH))).thenReturn(1);
        PasswordEditDTO dto=request(); dto.setEmpId(empId); service.editPassword(dto);
        verify(mapper).updatePassword(argThat(e -> e.getId().equals(empId)),eq(OLD_HASH));
    }
    @Test void absentLoginFailsBeforeDatabaseAccess() {
        BaseContext.removeCurrentId();
        assertThatThrownBy(() -> service.editPassword(request())).isInstanceOf(UserNotLoginException.class);
        verifyNoInteractions(mapper);
    }
    @Test void forgedEmployeeIdFailsBeforeReadingAnotherEmployeesPassword() {
        PasswordEditDTO dto=request(); dto.setEmpId(99L);
        assertThatThrownBy(() -> service.editPassword(dto)).isInstanceOf(PasswordEditFailedException.class);
        verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"dto","old","new","blankOld","blankNew"})
    void missingFieldsFailBeforeDatabaseAccess(String field) {
        PasswordEditDTO dto=request();
        switch(field) {
            case "dto": dto=null; break;
            case "old": dto.setOldPassword(null); break;
            case "new": dto.setNewPassword(null); break;
            case "blankOld": dto.setOldPassword(" "); break;
            case "blankNew": dto.setNewPassword(" "); break;
            default: throw new IllegalArgumentException(field);
        }
        final PasswordEditDTO invalid=dto;
        assertThatThrownBy(() -> service.editPassword(invalid)).isInstanceOf(PasswordEditFailedException.class);
        verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"abc12","A123456789012345678901","abc 123","abc$123","中文密码123"})
    void newPasswordMustMatchTheExistingFrontendFormat(String password) {
        PasswordEditDTO dto=request(); dto.setNewPassword(password);
        assertThatThrownBy(() -> service.editPassword(dto)).isInstanceOf(PasswordEditFailedException.class)
                .hasMessage(MessageConstant.PASSWORD_FORMAT_ERROR);
        verifyNoInteractions(mapper);
    }
    @Test void wrongOldPasswordDoesNotUpdate() {
        when(mapper.getById(USER_ID)).thenReturn(current(1)); PasswordEditDTO dto=request(); dto.setOldPassword("654321");
        assertThatThrownBy(() -> service.editPassword(dto)).isInstanceOf(PasswordErrorException.class).hasMessage(MessageConstant.PASSWORD_ERROR);
        verify(mapper,never()).updatePassword(any(),anyString());
    }
    @Test void unknownEmployeeDoesNotUpdate() {
        assertThatThrownBy(() -> service.editPassword(request())).isInstanceOf(AccountNotFoundException.class);
        verify(mapper,never()).updatePassword(any(),anyString());
    }
    @ParameterizedTest @NullSource @ValueSource(ints={0})
    void disabledOrInvalidAccountCannotChangePassword(Integer status) {
        when(mapper.getById(USER_ID)).thenReturn(current(status));
        assertThatThrownBy(() -> service.editPassword(request())).isInstanceOf(AccountLockedException.class);
        verify(mapper,never()).updatePassword(any(),anyString());
    }
    @Test void concurrentPasswordOrStatusChangeDoesNotReportSuccess() {
        when(mapper.getById(USER_ID)).thenReturn(current(1));
        assertThatThrownBy(() -> service.editPassword(request())).isInstanceOf(PasswordEditFailedException.class)
                .hasMessage(MessageConstant.PASSWORD_EDIT_FAILED);
        verify(mapper).updatePassword(any(),eq(OLD_HASH));
    }
}
