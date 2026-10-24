package com.azure.web;

import com.azure.context.BaseContext;
import com.azure.entity.*;
import com.azure.support.*;
import com.azure.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerMvcTest extends WebTestSupport {
    @Test void publicAccountLoginReturnsUserProfileAndToken() throws Exception {
        String token = JwtUtil.createJWT(WebTestConfiguration.USER_SECRET,60000,Map.of("userId",42L));
        when(userService.login(any())).thenReturn(com.azure.vo.UserLoginVO.builder()
                .id(42L).username("测试昵称").avatar(null).token(token).build());
        successful(post("/user/user/login").contentType("application/json")
                .content("{\"account\":\"user42\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.username").value("测试昵称"))
                .andExpect(jsonPath("$.data.token").value(token))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.openid").doesNotExist());
        verify(userService).login(argThat(dto -> dto.getAccount().equals("user42") && dto.getPassword().equals("123456")));
    }
    @Test void addressListUsesIdentityFromJwt() throws Exception {
        when(addressBookService.list(any())).thenAnswer(i -> {
            assertThat(BaseContext.getCurrentId()).isEqualTo(42L); return Collections.singletonList(AddressBook.builder().id(5L).detail("测试地址").build());
        });
        successful(user(get("/user/addressBook/list"))).andExpect(jsonPath("$.data[0].detail").value("测试地址"));
        verify(addressBookService).list(argThat(address -> address.getUserId()==42L));
    }
    @Test void addressCreateUpdateAndDefaultBindBody() throws Exception {
        String body = "{\"id\":5,\"consignee\":\"张三\",\"phone\":\"13800000000\",\"detail\":\"测试地址\",\"provinceName\":\"广东省\"}";
        successful(user(post("/user/addressBook").contentType("application/json").content(body)));
        successful(user(put("/user/addressBook").contentType("application/json").content(body)));
        successful(user(put("/user/addressBook/default").contentType("application/json").content("{\"id\":5}")));
        verify(addressBookService).save(argThat(address -> address.getPhone().equals("13800000000") && address.getProvinceName().equals("广东省")));
        verify(addressBookService).update(argThat(address -> address.getId()==5L && address.getDetail().equals("测试地址")));
        verify(addressBookService).setDefault(argThat(address -> address.getId()==5L));
    }
    @Test void addressGetAndDeleteBindId() throws Exception {
        when(addressBookService.getById(5L)).thenReturn(AddressBook.builder().id(5L).consignee("张三").build());
        successful(user(get("/user/addressBook/5"))).andExpect(jsonPath("$.data.consignee").value("张三"));
        successful(user(delete("/user/addressBook").param("id","5"))); verify(addressBookService).deleteById(5L);
    }
    @Test void defaultAddressQueriesUserAndDefaultFlag() throws Exception {
        when(addressBookService.list(any())).thenReturn(Collections.singletonList(AddressBook.builder().id(5L).build()));
        successful(user(get("/user/addressBook/default"))).andExpect(jsonPath("$.data.id").value(5));
        verify(addressBookService).list(argThat(address -> address.getUserId()==42L && address.getIsDefault()==1));
    }
    @ParameterizedTest @NullAndEmptySource
    void absentDefaultAddressUsesErrorEnvelope(List<AddressBook> rows) throws Exception {
        when(addressBookService.list(any())).thenReturn(rows);
        mvc.perform(user(get("/user/addressBook/default"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("没有查询到默认地址"));
    }
    @Test void ambiguousDefaultsUseErrorEnvelope() throws Exception {
        when(addressBookService.list(any())).thenReturn(Arrays.asList(new AddressBook(),new AddressBook()));
        mvc.perform(user(get("/user/addressBook/default"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }
    @Test void cartAddAndSubtractBindDishAndFlavor() throws Exception {
        String body = "{\"dishId\":7,\"dishFlavor\":\"微辣\"}";
        successful(user(post("/user/shoppingCart/add").contentType("application/json").content(body)));
        successful(user(post("/user/shoppingCart/sub").contentType("application/json").content(body)));
        verify(shoppingCartService).addShoppingCart(argThat(dto -> dto.getDishId()==7L && dto.getDishFlavor().equals("微辣")));
        verify(shoppingCartService).subShoppingCart(argThat(dto -> dto.getDishId()==7L && dto.getDishFlavor().equals("微辣")));
    }
    @Test void cartAddSupportsSetmeal() throws Exception {
        successful(user(post("/user/shoppingCart/add").contentType("application/json").content("{\"setmealId\":8}")));
        verify(shoppingCartService).addShoppingCart(argThat(dto -> dto.getSetmealId()==8L && dto.getDishId()==null));
    }
    @Test void cartListAndCleanDelegateToCurrentUserService() throws Exception {
        when(shoppingCartService.showShoppingCart()).thenReturn(Collections.singletonList(ShoppingCart.builder().dishId(7L).number(2).build()));
        successful(user(get("/user/shoppingCart/list"))).andExpect(jsonPath("$.data[0].number").value(2));
        successful(user(delete("/user/shoppingCart/clean"))); verify(shoppingCartService).cleanShoppingCart();
    }
    @ParameterizedTest @ValueSource(ints={0,1})
    void userShopStatusIsPublicAndAdminStatusUsesSameRedisKey(int state) throws Exception {
        when(values.get("SHOP_STATUS")).thenReturn(state);
        successful(get("/user/shop/status")).andExpect(jsonPath("$.data").value(state));
        successful(admin(get("/admin/shop/status"))).andExpect(jsonPath("$.data").value(state));
        successful(admin(put("/admin/shop/"+state))); verify(values).set("SHOP_STATUS",state);
    }
    @Test void missingShopStatusIsReadableBeforeUserLoginAndDefaultsToClosed() throws Exception {
        when(values.get("SHOP_STATUS")).thenReturn(null);
        successful(get("/user/shop/status")).andExpect(jsonPath("$.data").value(0));
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void missingShopStatusIsReadableForAdministratorAndDefaultsToClosed() throws Exception {
        when(values.get("SHOP_STATUS")).thenReturn(null);
        successful(admin(get("/admin/shop/status"))).andExpect(jsonPath("$.data").value(0));
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void statusInitializationMustNotOverwriteAnOpeningRequest() throws Exception {
        AtomicReference<Integer> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            stored.set((Integer) invocation.getArgument(1));
            return null;
        }).when(values).set(eq("SHOP_STATUS"),anyInt());
        when(values.get("SHOP_STATUS")).thenAnswer(invocation -> {
            Integer observed = stored.get();
            // PUT completes after GET observes a missing key, before GET returns its default.
            successful(admin(put("/admin/shop/1")));
            assertThat(stored.get()).isEqualTo(1);
            return observed;
        });
        successful(admin(get("/admin/shop/status")));
        assertThat(stored.get()).isEqualTo(1);
    }
}
