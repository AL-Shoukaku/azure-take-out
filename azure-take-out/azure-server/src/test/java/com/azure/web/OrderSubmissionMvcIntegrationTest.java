package com.azure.web;

import com.azure.AzureApplication;
import com.azure.constant.MessageConstant;
import com.azure.context.BaseContext;
import com.azure.entity.Orders;
import com.azure.properties.JwtProperties;
import com.azure.task.OrderTask;
import com.azure.utils.JwtUtil;
import com.azure.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual checkout through JWT, controllers, services and production SQL; no external services. */
@SpringBootTest(classes=AzureApplication.class,properties={
        "spring.datasource.druid.url=jdbc:h2:mem:azure_order_submission;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE",
        "spring.cache.type=simple"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderSubmissionMvcIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtProperties jwt;
    @Autowired private JdbcTemplate jdbc;
    // 测试用：通过 MockitoBean 替换测试上下文中的组件，保持原有模拟范围。
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean(name="serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;
    @MockitoBean private WebSocketServer webSocketServer;

    @BeforeEach void resetFixtures() {
        // Separate HTTP requests use separate production transactions and MyBatis sessions.
        new ResourceDatabasePopulator(new ClassPathResource("sql/schema.sql"),new ClassPathResource("sql/data.sql"))
                .execute(jdbc.getDataSource());
    }
    @AfterEach void clearIdentity() { BaseContext.removeCurrentId(); }

    private MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header(jwt.getUserTokenName(),JwtUtil.createJWT(jwt.getUserSecretKey(),60000,
                new HashMap<>(Collections.singletonMap("userId",42L))));
    }
    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header(jwt.getAdminTokenName(),JwtUtil.createJWT(jwt.getAdminSecretKey(),60000,
                new HashMap<>(Collections.singletonMap("empId",7L))));
    }
    private MockHttpServletRequestBuilder submit() {
        return user(post("/user/order/submit").contentType("application/json")
                .content("{\"addressBookId\":1,\"payMethod\":1,\"deliveryStatus\":1,\"tablewareStatus\":1,\"tablewareNumber\":2}"));
    }
    private void clearCart() throws Exception {
        mvc.perform(user(delete("/user/shoppingCart/clean"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }
    private void add(String productId,long id) throws Exception {
        mvc.perform(user(post("/user/shoppingCart/add").contentType("application/json")
                .content("{\""+productId+"\":"+id+"}"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }
    private void prepareMixedCart() throws Exception {
        clearCart(); add("dishId",1L); add("setmealId",1L);
    }
    private int count(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }
    private List<Map<String,Object>> cartSnapshot() { return jdbc.queryForList("select * from shopping_cart order by id"); }
    private void rejectedWithoutChangingOrdersOrCart() throws Exception {
        int orders=count("orders"),details=count("order_detail");
        List<Map<String,Object>> cart=cartSnapshot();
        mvc.perform(submit()).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.SHOPPING_CART_PRODUCT_UNAVAILABLE));
        assertThat(count("orders")).isEqualTo(orders);
        assertThat(count("order_detail")).isEqualTo(details);
        assertThat(cartSnapshot()).isEqualTo(cart);
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    private void mixedCartSuccessfullySubmitted() throws Exception {
        int orders=count("orders"),details=count("order_detail");
        List<Map<String,Object>> otherCart=jdbc.queryForList("select * from shopping_cart where user_id=43 order by id");
        mvc.perform(submit()).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.orderAmount").value(50.40));
        assertThat(count("orders")).isEqualTo(orders+1);
        assertThat(count("order_detail")).isEqualTo(details+2);
        assertThat(jdbc.queryForObject("select count(*) from shopping_cart where user_id=42",Integer.class)).isZero();
        assertThat(jdbc.queryForList("select * from shopping_cart where user_id=43 order by id")).isEqualTo(otherCart);
        Long orderId=jdbc.queryForObject("select max(id) from orders",Long.class);
        assertThat(jdbc.queryForObject("select amount from orders where id=?",BigDecimal.class,orderId)).isEqualByComparingTo("50.40");
        assertThat(jdbc.queryForObject("select status from orders where id=?",Integer.class,orderId)).isEqualTo(Orders.PENDING_PAYMENT);
        assertThat(jdbc.queryForObject("select count(*) from order_detail where order_id=? and dish_id=1",Integer.class,orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_detail where order_id=? and setmeal_id=1",Integer.class,orderId)).isEqualTo(1);
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @ParameterizedTest @ValueSource(strings={"dish","setmeal"})
    void stoppingAnItemAlreadyInCartRejectsCheckoutAndRestockingAllowsRetry(String table) throws Exception {
        prepareMixedCart();
        jdbc.update("update "+table+" set status=0 where id=1");
        rejectedWithoutChangingOrdersOrCart();
        jdbc.update("update "+table+" set status=1 where id=1");
        mixedCartSuccessfullySubmitted();
    }
    @ParameterizedTest @ValueSource(strings={"dish","setmeal"})
    void productDeletedAfterAddingToCartCannotBeOrdered(String table) throws Exception {
        prepareMixedCart();
        jdbc.update("delete from "+table+" where id=1");
        rejectedWithoutChangingOrdersOrCart();
    }
    @Test void allOnSaleDishesAndSetmealsCanBeSubmittedTogether() throws Exception {
        prepareMixedCart();
        mixedCartSuccessfullySubmitted();
    }
    private void enableSetmealContainingStoppedDish() throws Exception {
        assertThat(jdbc.queryForObject("select status from dish where id=3",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select dish_id from setmeal_dish where setmeal_id=2",Long.class)).isEqualTo(3L);
        mvc.perform(admin(post("/admin/setmeal/status/1").param("id","2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThat(jdbc.queryForObject("select status from setmeal where id=2",Integer.class)).isEqualTo(1);
        clearCart(); add("setmealId",2L);
    }
    @Test void onSaleSetmealContainingStoppedDishCanBeEnabledAndOrdered() throws Exception {
        enableSetmealContainingStoppedDish();
        int orders=count("orders"),details=count("order_detail");
        mvc.perform(submit()).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.orderAmount").value(46.90));
        Long orderId=jdbc.queryForObject("select max(id) from orders",Long.class);
        assertThat(count("orders")).isEqualTo(orders+1);
        assertThat(count("order_detail")).isEqualTo(details+1);
        assertThat(jdbc.queryForObject("select setmeal_id from order_detail where order_id=?",Long.class,orderId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select dish_id from order_detail where order_id=?",Long.class,orderId)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from shopping_cart where user_id=42",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from shopping_cart where user_id=43",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from dish where id=3",Integer.class)).isZero();
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void stoppedComponentAlsoBoughtAsStandaloneDishStillBlocksWholeOrder() throws Exception {
        enableSetmealContainingStoppedDish();
        add("dishId",3L);
        rejectedWithoutChangingOrdersOrCart();
    }
}
