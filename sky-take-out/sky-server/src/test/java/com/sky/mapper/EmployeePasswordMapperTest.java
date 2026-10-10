package com.sky.mapper;

import com.sky.entity.Employee;
import com.sky.support.SqlTestSupport;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class EmployeePasswordMapperTest extends SqlTestSupport {
    private static final String OLD_HASH="e10adc3949ba59abbe56e057f20f883e";
    private static final String NEW_HASH="d9c04a262cd6a4b77c222172288f4f41";
    @Test void conditionalPasswordUpdateSucceedsOnceAndPreservesAccountProfile() {
        EmployeeMapper mapper=mapper(EmployeeMapper.class);
        Employee change=Employee.builder().id(1L).password(NEW_HASH).updateUser(1L).updateTime(LocalDateTime.of(2026,10,8,16,0)).build();
        assertThat(mapper.updatePassword(change,OLD_HASH)).isEqualTo(1);
        assertThat(mapper.updatePassword(Employee.builder().id(1L).password("another-hash").build(),OLD_HASH)).isZero();
        Employee saved=mapper.getById(1L);
        assertThat(saved.getPassword()).isEqualTo(NEW_HASH); assertThat(saved.getUpdateUser()).isEqualTo(1L);
        assertThat(saved.getUpdateTime()).isEqualTo(change.getUpdateTime());
        assertThat(saved.getUsername()).isEqualTo("admin"); assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getPhone()).isEqualTo("13800000000");
        assertThat(mapper.getById(2L).getPassword()).isEqualTo(OLD_HASH);
    }
    @Test void wrongOldPasswordMissingEmployeeAndDisabledStatusCannotUpdate() {
        EmployeeMapper mapper=mapper(EmployeeMapper.class);
        assertThat(mapper.updatePassword(Employee.builder().id(1L).password(NEW_HASH).build(),"wrong-hash")).isZero();
        assertThat(mapper.updatePassword(Employee.builder().id(999L).password(NEW_HASH).build(),OLD_HASH)).isZero();
        assertThat(mapper.updatePassword(Employee.builder().id(2L).password(NEW_HASH).build(),OLD_HASH)).isZero();
        assertThat(mapper.getById(1L).getPassword()).isEqualTo(OLD_HASH);
        assertThat(mapper.getById(2L).getPassword()).isEqualTo(OLD_HASH);
    }
}
