package com.azure.aspect;

import com.azure.entity.*;
import com.azure.mapper.*;
import com.azure.support.ServiceTestSupport;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutoFillAspectTest extends ServiceTestSupport {
    private final AutoFillAspect aspect = new AutoFillAspect();
    private void invoke(Method method,Object... args) {
        JoinPoint point = mock(JoinPoint.class); MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature); when(signature.getMethod()).thenReturn(method); when(point.getArgs()).thenReturn(args);
        aspect.autoFill(point);
    }
    private static Stream<Arguments> inserts() throws Exception {
        return Stream.of(Arguments.of(EmployeeMapper.class.getMethod("insert",Employee.class),new Employee()),
                Arguments.of(CategoryMapper.class.getMethod("insert",Category.class),new Category()),
                Arguments.of(DishMapper.class.getMethod("Insert",Dish.class),new Dish()),
                Arguments.of(SetmealMapper.class.getMethod("save",Setmeal.class),new Setmeal()));
    }
    @ParameterizedTest @MethodSource("inserts")
    void insertSetsAllFourAuditFields(Method method,Object entity) throws Exception {
        LocalDateTime before = LocalDateTime.now(); invoke(method,entity);
        LocalDateTime created = (LocalDateTime)entity.getClass().getMethod("getCreateTime").invoke(entity);
        assertThat(created).isBetween(before,LocalDateTime.now());
        assertThat(entity.getClass().getMethod("getUpdateTime").invoke(entity)).isEqualTo(created);
        assertThat(entity.getClass().getMethod("getCreateUser").invoke(entity)).isEqualTo(USER_ID);
        assertThat(entity.getClass().getMethod("getUpdateUser").invoke(entity)).isEqualTo(USER_ID);
    }
    @Test void updatePreservesCreationAudit() throws Exception {
        LocalDateTime original = LocalDateTime.of(2025,1,1,0,0);
        Employee entity = Employee.builder().createTime(original).createUser(7L).build();
        LocalDateTime before = LocalDateTime.now(); invoke(EmployeeMapper.class.getMethod("update",Employee.class),entity);
        assertThat(entity.getCreateTime()).isEqualTo(original); assertThat(entity.getCreateUser()).isEqualTo(7L);
        assertThat(entity.getUpdateUser()).isEqualTo(USER_ID); assertThat(entity.getUpdateTime()).isBetween(before,LocalDateTime.now());
    }
    @Test void emptyArgumentsDoNotFail() throws Exception { invoke(EmployeeMapper.class.getMethod("insert",Employee.class)); }
    @Test void missingEntitySettersAreReported() throws Exception {
        assertThatThrownBy(() -> invoke(EmployeeMapper.class.getMethod("insert",Employee.class),new Object()))
                .isInstanceOf(RuntimeException.class).hasCauseInstanceOf(NoSuchMethodException.class);
    }
}
