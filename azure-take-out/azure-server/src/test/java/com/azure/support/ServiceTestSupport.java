package com.azure.support;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.azure.context.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Arrays;

/** Every test owns its request context; PageHelper cannot leak into the next test. */
@ExtendWith(MockitoExtension.class)
public abstract class ServiceTestSupport {
    protected static final long USER_ID = 42L;

    @BeforeEach
    void initializeRequestContext() { BaseContext.setCurrentId(USER_ID); }

    @AfterEach
    void clearRequestContext() {
        BaseContext.removeCurrentId();
        PageHelper.clearPage();
    }

    @SafeVarargs
    protected static <T> Page<T> page(long total, T... records) {
        Page<T> page = new Page<>();
        page.addAll(Arrays.asList(records));
        page.setTotal(total);
        return page;
    }
}
