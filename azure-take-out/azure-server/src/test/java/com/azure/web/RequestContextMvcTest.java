package com.azure.web;

import com.azure.context.BaseContext;
import com.azure.exception.BaseException;
import com.azure.support.WebTestConfiguration;
import com.azure.support.WebTestSupport;
import com.azure.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Collections;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RequestContextMvcTest extends WebTestSupport {
    private MockHttpServletRequestBuilder authenticatedRequest(String role) {
        MockHttpServletRequestBuilder request = get("/" + role + "/category/list");
        return "admin".equals(role) ? admin(request) : user(request);
    }

    private long identity(String role) {
        return "admin".equals(role) ? 7L : 42L;
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "user"})
    void successfulRequestKeepsIdentityDuringServiceAndRemovesItAfterCompletion(String role) throws Exception {
        when(categoryService.list(null)).thenAnswer(invocation -> {
            assertThat(BaseContext.getCurrentId()).isEqualTo(identity(role));
            return Collections.emptyList();
        });

        successful(authenticatedRequest(role));

        verify(categoryService).list(null);
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "user"})
    void handledBusinessFailureRemovesIdentity(String role) throws Exception {
        when(categoryService.list(null)).thenAnswer(invocation -> {
            assertThat(BaseContext.getCurrentId()).isEqualTo(identity(role));
            throw new BaseException("分类查询失败");
        });

        mvc.perform(authenticatedRequest(role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("分类查询失败"));

        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "user"})
    void unhandledServiceFailureStillRemovesIdentity(String role) {
        IllegalStateException failure = new IllegalStateException("unexpected service failure");
        when(categoryService.list(null)).thenAnswer(invocation -> {
            assertThat(BaseContext.getCurrentId()).isEqualTo(identity(role));
            throw failure;
        });

        assertThatThrownBy(() -> mvc.perform(authenticatedRequest(role))).hasRootCause(failure);

        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "user"})
    void parameterBindingFailureRemovesIdentityBeforeReturningBadRequest(String role) throws Exception {
        mvc.perform(authenticatedRequest(role).param("type", "invalid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"admin,missing", "user,missing", "admin,malformed", "user,malformed",
            "admin,expired", "user,expired", "admin,wrongClaim", "user,wrongClaim"})
    void rejectedTokenCannotLeaveAnExistingIdentityOnTheRequestThread(String role, String kind) throws Exception {
        BaseContext.setCurrentId(999L);
        MockHttpServletRequestBuilder request = get("/" + role + "/category/list");
        if (!"missing".equals(kind)) {
            String token = "invalid";
            if (!"malformed".equals(kind)) {
                String secret = "admin".equals(role) ? WebTestConfiguration.ADMIN_SECRET : WebTestConfiguration.USER_SECRET;
                String claim = "wrongClaim".equals(kind) ? "otherId" : "admin".equals(role) ? "empId" : "userId";
                long ttl = "expired".equals(kind) ? -60000 : 60000;
                token = JwtUtil.createJWT(secret, ttl, new HashMap<>(Collections.singletonMap(claim, identity(role))));
            }
            request.header("admin".equals(role) ? "token" : "authentication", token);
        }

        mvc.perform(request).andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test
    void sequentialAdminUserAndPublicRequestsDoNotShareIdentity() throws Exception {
        when(categoryService.list(null)).thenAnswer(invocation -> {
            assertThat(BaseContext.getCurrentId()).isEqualTo(7L);
            return Collections.emptyList();
        }).thenAnswer(invocation -> {
            assertThat(BaseContext.getCurrentId()).isEqualTo(42L);
            return Collections.emptyList();
        });
        when(shopService.getShopStatus()).thenAnswer(invocation -> {
            assertThat(BaseContext.getCurrentId()).isNull();
            return 1;
        });

        successful(authenticatedRequest("admin"));
        assertThat(BaseContext.getCurrentId()).isNull();
        successful(authenticatedRequest("user"));
        assertThat(BaseContext.getCurrentId()).isNull();
        successful(get("/user/shop/status"));
        assertThat(BaseContext.getCurrentId()).isNull();
        verify(categoryService, times(2)).list(null);
    }
}
