package com.azure.mapper;

import com.azure.entity.Employee;
import com.azure.security.PasswordHasher;
import com.azure.support.SqlTestSupport;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class EmployeePasswordMapperTest extends SqlTestSupport {
    private static final String OLD_HASH="e10adc3949ba59abbe56e057f20f883e";
    @Test void updateByIdPersistsFullArgon2idHashAndPreservesAccountProfile() {
        EmployeeMapper mapper=mapper(EmployeeMapper.class);
        PasswordHasher passwordHasher = new PasswordHasher();
        String newHash = passwordHasher.encode("New123");
        Employee change=Employee.builder().id(1L).password(newHash).updateUser(1L).updateTime(LocalDateTime.of(2026,10,8,16,0)).build();
        assertThat(mapper.update(change)).isEqualTo(1);
        Employee saved=mapper.getById(1L);
        assertThat(saved.getPassword()).isEqualTo(newHash);
        assertThat(passwordHasher.matches("New123", saved.getPassword())).isTrue();
        assertThat(saved.getUpdateUser()).isEqualTo(1L);
        assertThat(saved.getUpdateTime()).isEqualTo(change.getUpdateTime());
        assertThat(saved.getUsername()).isEqualTo("admin"); assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getPhone()).isEqualTo("13800000000");
        assertThat(mapper.getById(2L).getPassword()).isEqualTo(OLD_HASH);
    }
    @Test void missingEmployeeIdDoesNotUpdateOtherAccounts() {
        EmployeeMapper mapper=mapper(EmployeeMapper.class);
        assertThat(mapper.update(Employee.builder().id(999L).password("unused-hash").build())).isZero();
        assertThat(mapper.update(Employee.builder().password("unused-hash").build())).isZero();
        assertThat(mapper.getById(1L).getPassword()).isEqualTo(OLD_HASH);
        assertThat(mapper.getById(2L).getPassword()).isEqualTo(OLD_HASH);
    }
}
