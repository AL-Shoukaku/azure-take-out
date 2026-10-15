package com.sky.web;

import com.sky.support.*;
import com.sky.utils.JwtUtil;
import com.sky.exception.OrderBusinessException;
import com.sky.context.BaseContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationMvcTest extends WebTestSupport {
    @ParameterizedTest @ValueSource(strings={"/admin/employee/page","/admin/category/list","/admin/dish/list","/admin/setmeal/page",
            "/admin/shop/status","/admin/order/statistics","/admin/report/top10","/admin/workspace/overviewDishes",
            "/user/category/list","/user/dish/list","/user/setmeal/list","/user/addressBook/list","/user/shoppingCart/list","/user/order/historyOrders"})
    void everyProtectedControllerRejectsMissingToken(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(employeeService,categoryService,dishService,setmealService,addressBookService,shoppingCartService,ordersService,reportService,workspaceService);
    }
    @ParameterizedTest @ValueSource(strings={"", "invalid", "a.b.c"})
    void malformedTokensAreRejectedOnBothEnds(String token) throws Exception {
        mvc.perform(get("/admin/category/list").header("token",token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/category/list").header("authentication",token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService);
    }

    @ParameterizedTest
    @CsvSource({"/admin/category/list,token,empId,7", "/user/category/list,authentication,userId,42"})
    void tamperedIdentityIsRejectedBeforeService(String path, String header, String claim, long id) throws Exception {
        String secret = "empId".equals(claim)
                ? WebTestConfiguration.ADMIN_SECRET : WebTestConfiguration.USER_SECRET;
        String token = JwtUtil.createJWT(secret, 60000, Map.of(claim, id));
        String[] parts = token.split("\\.");
        ObjectNode payload = (ObjectNode) json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        payload.put(claim, id + 1);
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.writeValueAsBytes(payload));

        mvc.perform(get(path).header(header, String.join(".", parts)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService);
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"/admin/category/list,token,empId,7", "/user/category/list,authentication,userId,42"})
    void unsignedTokensAreRejectedBeforeService(String path, String header, String claim, long id) throws Exception {
        String unsigned = Jwts.builder()
                .claims(Map.of(claim, id))
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .compact();

        mvc.perform(get(path).header(header, unsigned)).andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService);
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test void expiredTokensAreRejectedWithoutSleeping() throws Exception {
        mvc.perform(get("/admin/category/list").header("token",JwtUtil.createJWT(WebTestConfiguration.ADMIN_SECRET,-60000,new HashMap<>(Collections.singletonMap("empId",7L))))).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/category/list").header("authentication",JwtUtil.createJWT(WebTestConfiguration.USER_SECRET,-60000,new HashMap<>(Collections.singletonMap("userId",42L))))).andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService);
    }
    @Test void tokensCannotBeUsedAcrossAdminAndUserSecrets() throws Exception {
        String adminToken=JwtUtil.createJWT(WebTestConfiguration.ADMIN_SECRET,60000,new HashMap<>(Collections.singletonMap("empId",7L)));
        String userToken=JwtUtil.createJWT(WebTestConfiguration.USER_SECRET,60000,new HashMap<>(Collections.singletonMap("userId",42L)));
        mvc.perform(get("/user/category/list").header("authentication",adminToken)).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/category/list").header("token",userToken)).andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService);
    }
    @Test void missingIdentityClaimIsRejectedEvenWithValidSignature() throws Exception {
        mvc.perform(get("/admin/category/list").header("token",JwtUtil.createJWT(WebTestConfiguration.ADMIN_SECRET,60000,new HashMap<>(Collections.singletonMap("userId",42L))))).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/category/list").header("authentication",JwtUtil.createJWT(WebTestConfiguration.USER_SECRET,60000,new HashMap<>(Collections.singletonMap("empId",7L))))).andExpect(status().isUnauthorized());
    }
    @Test void wrongHeaderNameDoesNotAuthenticate() throws Exception {
        mvc.perform(user(get("/admin/category/list"))).andExpect(status().isUnauthorized());
        mvc.perform(admin(get("/user/category/list"))).andExpect(status().isUnauthorized());
    }
    @Test void businessExceptionsRetainExistingHttp200AndErrorEnvelope() throws Exception {
        when(ordersService.queryUserOrderDetail(9L)).thenThrow(new OrderBusinessException("订单不存在"));
        mvc.perform(user(get("/user/order/orderDetail/9"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("订单不存在"));
    }
    @Test void invalidJsonAndInvalidQueryTypesAreRejectedBeforeService() throws Exception {
        mvc.perform(admin(post("/admin/employee").contentType("application/json").content("{"))).andExpect(status().isBadRequest());
        mvc.perform(admin(get("/admin/employee/page").param("page","bad"))).andExpect(status().isBadRequest());
        verifyNoInteractions(employeeService);
    }
    @Test void requiredSetmealQueryParameterIsEnforced() throws Exception {
        mvc.perform(admin(post("/admin/setmeal/status/1"))).andExpect(status().isBadRequest()); verifyNoInteractions(setmealService);
    }
    @Test void unimplementedUserLogoutIsNotInventedByTestConfiguration() throws Exception {
        mvc.perform(user(post("/user/user/logout"))).andExpect(status().isNotFound());
    }
}
