package com.azure.web;

import com.azure.constant.MessageConstant;
import com.azure.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import java.sql.SQLIntegrityConstraintViolationException;
import static org.assertj.core.api.Assertions.*;

class ExceptionHandlerTest {
    private final GlobalExceptionHandler handler=new GlobalExceptionHandler();
    @Test void duplicateUsernameIncludesExistingValue() {
        assertThat(handler.exceptionHandler(new SQLIntegrityConstraintViolationException("Duplicate entry 'alice' for key 'employee.idx_username'")).getMsg())
                .isEqualTo("'alice'"+MessageConstant.ALREADY_EXISTS);
    }
    @Test void otherConstraintViolationsUseUnknownErrorEnvelope() {
        assertThat(handler.exceptionHandler(new SQLIntegrityConstraintViolationException("Column cannot be null")).getMsg()).isEqualTo(MessageConstant.UNKNOWN_ERROR);
    }
}
