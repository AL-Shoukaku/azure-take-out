package com.azure.web;

import com.azure.AzureApplication;
import com.azure.context.BaseContext;
import com.azure.constant.MessageConstant;
import com.azure.entity.AddressBook;
import com.azure.entity.Orders;
import com.azure.mapper.AddressBookMapper;
import com.azure.mapper.OrdersMapper;
import com.azure.service.AddressBookService;
import com.azure.task.OrderTask;
import com.azure.utils.JwtUtil;
import com.azure.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT interceptors, controllers, services and production MyBatis SQL, with isolated H2. */
@SpringBootTest(classes=AzureApplication.class, properties={
        "spring.datasource.druid.url=jdbc:h2:mem:azure_ownership_mvc;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnershipMvcIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private com.azure.properties.JwtProperties jwt;
    @Autowired private OrdersMapper orders;
    @Autowired private AddressBookMapper addresses;
    @Autowired private AddressBookService addressService;
    @Autowired private JdbcTemplate jdbc;
    // 测试用：通过 MockitoBean 替换测试上下文中的组件，保持原有模拟范围。
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean(name="serverEndpointExporter") private ServerEndpointExporter serverEndpointExporter;
    @MockitoBean private OrderTask orderTask;
    @MockitoBean private WebSocketServer webSocketServer;
    @AfterEach void clearIdentity() { BaseContext.removeCurrentId(); }

    private MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request) {
        return request.header(jwt.getUserTokenName(),JwtUtil.createJWT(jwt.getUserSecretKey(),60000,
                new HashMap<>(Collections.singletonMap("userId",42L))));
    }
    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request,long employeeId) {
        return request.header(jwt.getAdminTokenName(),JwtUtil.createJWT(jwt.getAdminSecretKey(),60000,
                new HashMap<>(Collections.singletonMap("empId",employeeId))));
    }
    private void rejected(MockHttpServletRequestBuilder request,String message) throws Exception {
        mvc.perform(user(request)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value(message));
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    private int count(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }

    @Test void completingDeliveryPersistsTimeAndRejectsRepeatedCompletion() throws Exception {
        jdbc.update("update orders set status=4,pay_status=1,delivery_time=null where id=1");
        LocalDateTime before=LocalDateTime.now();
        mvc.perform(admin(put("/admin/order/complete/1"),7L)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        Orders completed=orders.getById(1L);
        assertThat(completed.getStatus()).isEqualTo(Orders.COMPLETED);
        LocalDateTime delivered=completed.getDeliveryTime();
        assertThat(delivered).isBetween(before.minusSeconds(1),LocalDateTime.now().plusSeconds(1));
        mvc.perform(admin(get("/admin/order/details/1"),7L)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryTime").value(delivered.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
        mvc.perform(admin(put("/admin/order/complete/1"),7L)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value(MessageConstant.ORDER_STATUS_ERROR));
        assertThat(orders.getById(1L).getDeliveryTime()).isEqualTo(delivered);
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @Test void wrongAccountPasswordReturnsLoginErrorWithoutCreatingUserOrToken() throws Exception {
        List<Map<String,Object>> users=jdbc.queryForList("select * from `user` order by id");
        mvc.perform(post("/user/user/login").contentType("application/json")
                .content("{\"account\":\"user42\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.PASSWORD_ERROR))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(jdbc.queryForList("select * from `user` order by id")).isEqualTo(users);
        assertThat(BaseContext.getCurrentId()).isNull();
    }

    @ParameterizedTest @ValueSource(longs={7L,42L,43L})
    void administratorCanQueryEveryUsersOrderRegardlessOfEmployeeId(long employeeId) throws Exception {
        mvc.perform(admin(get("/admin/order/details/1"),employeeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data.userId").value(42));
        mvc.perform(admin(get("/admin/order/details/3"),employeeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data.userId").value(43));
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void userCanQueryOwnOrderIncludingItsItems() throws Exception {
        mvc.perform(user(get("/user/order/orderDetail/1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.orderDetailList[0].dishId").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"detail","cancel","remind","payment","repetition"})
    void foreignOrderCannotBeReadOrModifiedAndDoesNotChangeCartOrSendMessages(String action) throws Exception {
        MockHttpServletRequestBuilder request;
        switch(action) {
            case "detail": request=get("/user/order/orderDetail/3"); break;
            case "cancel": request=put("/user/order/cancel/3"); break;
            case "remind": request=get("/user/order/reminder/3"); break;
            case "payment": request=put("/user/order/payment").contentType("application/json").content("{\"orderNumber\":\"order-3\"}"); break;
            case "repetition": request=post("/user/order/repetition/3"); break;
            default: throw new IllegalArgumentException(action);
        }
        rejected(request,"订单不存在");
        assertThat(orders.getById(3L).getStatus()).isEqualTo(5);
        assertThat(orders.getById(3L).getPayStatus()).isEqualTo(1);
        assertThat(count("orders")).isEqualTo(4); assertThat(count("shopping_cart")).isEqualTo(3);
        verifyNoInteractions(webSocketServer);
    }
    @ParameterizedTest @ValueSource(strings={"detail","update","default","delete"})
    void foreignAddressCannotBeAccessedEvenWithForgedRequestUserId(String action) throws Exception {
        MockHttpServletRequestBuilder request;
        switch(action) {
            case "detail": request=get("/user/addressBook/3"); break;
            case "update": request=put("/user/addressBook").contentType("application/json").content("{\"id\":3,\"userId\":42,\"detail\":\"越权修改\"}"); break;
            case "default": request=put("/user/addressBook/default").contentType("application/json").content("{\"id\":3,\"userId\":42}"); break;
            case "delete": request=delete("/user/addressBook").param("id","3"); break;
            default: throw new IllegalArgumentException(action);
        }
        rejected(request,"地址不存在");
        assertThat(addresses.getById(3L).getDetail()).isEqualTo("他人地址");
        assertThat(addresses.getById(3L).getUserId()).isEqualTo(43L);
        assertThat(addresses.getById(1L).getIsDefault()).isEqualTo(1);
        assertThat(addresses.getById(2L).getIsDefault()).isZero();
        assertThat(addresses.getById(3L).getIsDefault()).isEqualTo(1);
    }
    @Test void submittingWithForeignAddressKeepsOrdersDetailsAndCartUnchanged() throws Exception {
        rejected(post("/user/order/submit").contentType("application/json").content("{\"addressBookId\":3}"),"地址不存在");
        assertThat(count("orders")).isEqualTo(4); assertThat(count("order_detail")).isEqualTo(5);
        assertThat(count("shopping_cart")).isEqualTo(3);
    }
    @Test void missingAndForeignResourcesUseTheSameBusinessResponse() throws Exception {
        rejected(get("/user/order/orderDetail/999"),"订单不存在");
        rejected(get("/user/order/orderDetail/3"),"订单不存在");
        rejected(get("/user/addressBook/999"),"地址不存在");
        rejected(get("/user/addressBook/3"),"地址不存在");
    }
    @Test void updatingOwnAddressIgnoresForgedOwner() throws Exception {
        mvc.perform(user(put("/user/addressBook").contentType("application/json")
                .content("{\"id\":1,\"userId\":43,\"detail\":\"新详细地址\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThat(addresses.getById(1L).getUserId()).isEqualTo(42L);
        assertThat(addresses.getById(1L).getDetail()).isEqualTo("新详细地址");
        assertThat(addresses.getById(3L).getDetail()).isEqualTo("他人地址");
    }
    @Test void addressRegionEditCanBeReadBackThroughTheUserApi() throws Exception {
        mvc.perform(user(put("/user/addressBook").contentType("application/json")
                .content("{\"id\":1,\"consignee\":\"张三\",\"phone\":\"13800000000\",\"detail\":\"新详细地址\","
                        + "\"provinceCode\":\"440000\",\"provinceName\":\"广东省\",\"cityCode\":\"440100\","
                        + "\"cityName\":\"广州市\",\"districtCode\":\"440106\",\"districtName\":\"天河区\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mvc.perform(user(get("/user/addressBook/1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provinceCode").value("440000"))
                .andExpect(jsonPath("$.data.provinceName").value("广东省"))
                .andExpect(jsonPath("$.data.cityCode").value("440100"))
                .andExpect(jsonPath("$.data.cityName").value("广州市"))
                .andExpect(jsonPath("$.data.districtCode").value("440106"))
                .andExpect(jsonPath("$.data.districtName").value("天河区"))
                .andExpect(jsonPath("$.data.detail").value("新详细地址"));
        assertThat(addresses.getById(1L).getIsDefault()).isEqualTo(1);
        assertThat(addresses.getById(1L).getUserId()).isEqualTo(42L);
        assertThat(addresses.getById(3L).getDetail()).isEqualTo("他人地址");
    }
    private MockHttpServletRequestBuilder cancellationRequest(String action) {
        if (action.startsWith("user")) return user(put("/user/order/cancel/1"));
        if (action.equals("reject")) return admin(put("/admin/order/rejection").contentType("application/json")
                .content("{\"id\":1,\"rejectionReason\":\"售罄\"}"),7L);
        return admin(put("/admin/order/cancel").contentType("application/json")
                .content("{\"id\":1,\"cancelReason\":\"停业\"}"),7L);
    }
    @ParameterizedTest @ValueSource(strings={"userPendingPayment","userPaid","adminPendingPayment","adminConfirmed","adminDeliveryInProgress","adminCompleted","reject"})
    void cancellationPersistsTimeAndRepeatedRequestsKeepOriginalTime(String action) throws Exception {
        int source;
        if (action.equals("userPendingPayment") || action.equals("adminPendingPayment")) source = Orders.PENDING_PAYMENT;
        else if (action.equals("userPaid") || action.equals("reject")) source = Orders.TO_BE_CONFIRMED;
        else if (action.equals("adminConfirmed")) source = Orders.CONFIRMED;
        else if (action.equals("adminDeliveryInProgress")) source = Orders.DELIVERY_IN_PROGRESS;
        else source = Orders.COMPLETED;
        int originalPayStatus = source == Orders.PENDING_PAYMENT ? Orders.UN_PAID : Orders.PAID;
        jdbc.update("update orders set status=?,pay_status=? where id=1",source,originalPayStatus);
        LocalDateTime before = LocalDateTime.now();
        mvc.perform(cancellationRequest(action)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        Orders saved = orders.getById(1L);
        assertThat(saved.getStatus()).isEqualTo(Orders.CANCELLED);
        assertThat(saved.getCancelTime()).isBetween(before.minusSeconds(1),LocalDateTime.now().plusSeconds(1));
        assertThat(saved.getPayStatus()).isEqualTo(source == Orders.PENDING_PAYMENT ? Orders.UN_PAID : Orders.REFUND);
        if (action.equals("reject")) assertThat(saved.getRejectionReason()).isEqualTo("售罄");
        else if (action.startsWith("admin")) assertThat(saved.getCancelReason()).isEqualTo("停业");
        else assertThat(saved.getCancelReason()).isEqualTo("用户在接单前取消");
        mvc.perform(admin(get("/admin/order/details/1"),7L)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelTime").isNotEmpty());
        mvc.perform(cancellationRequest(action)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertThat(orders.getById(1L).getCancelTime()).isEqualTo(saved.getCancelTime());
        assertThat(orders.getById(3L).getStatus()).isEqualTo(Orders.COMPLETED);
        assertThat(orders.getById(3L).getCancelTime()).isNull();
        assertThat(BaseContext.getCurrentId()).isNull();
    }
    @Test void defaultAddressChangeOnlyAffectsCurrentUserAndDoesNotCopyUnrelatedRequestFields() throws Exception {
        mvc.perform(user(put("/user/addressBook/default").contentType("application/json")
                .content("{\"id\":2,\"userId\":43,\"detail\":\"不应保存\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThat(addresses.getById(1L).getIsDefault()).isZero();
        assertThat(addresses.getById(2L).getIsDefault()).isEqualTo(1);
        assertThat(addresses.getById(2L).getDetail()).isEqualTo("测试路2号");
        assertThat(addresses.getById(3L).getIsDefault()).isEqualTo(1);
    }
    @Test void deletingOwnAddressKeepsOtherUsersAddresses() throws Exception {
        mvc.perform(user(delete("/user/addressBook").param("id","2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThat(addresses.getById(2L)).isNull(); assertThat(addresses.getById(3L)).isNotNull();
    }
    @Test void listServiceOverwritesForgedOwnerEvenWhenCalledOutsideController() {
        BaseContext.setCurrentId(42L);
        assertThat(addressService.list(AddressBook.builder().userId(43L).build()))
                .extracting(AddressBook::getId).containsExactly(1L,2L);
    }
    @Test void ownSimulatedPaymentUpdatesOrderAndPreservesResponse() throws Exception {
        mvc.perform(user(put("/user/order/payment").contentType("application/json").content("{\"orderNumber\":\"order-1\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.packageStr").value("prepay_id=fake_prepay_id"));
        assertThat(orders.getById(1L).getStatus()).isEqualTo(2);
        assertThat(orders.getById(1L).getPayStatus()).isEqualTo(1);
        verify(webSocketServer).sendToAllClient(anyString());
    }
    @Test void historyPreservesLargeUserIdInsteadOfMatchingItsTruncatedIntegerValue() throws Exception {
        long largeId=(1L << 32)+42L;
        jdbc.update("insert into `user` (id,account,password,username,name,status) select ?,?,password,?,?,1 from `user` where id=42",
                largeId,"large-account","大 ID 昵称","大 ID 用户");
        AddressBook address=addresses.getById(1L); address.setUserId(largeId); addresses.insert(address);
        Orders largeOrder=orders.getById(1L); largeOrder.setId(null); largeOrder.setNumber("large-user-order");
        largeOrder.setUserId(largeId); largeOrder.setAddressBookId(addresses.list(AddressBook.builder().userId(largeId).build()).get(0).getId());
        orders.submitOrder(largeOrder);
        String token=JwtUtil.createJWT(jwt.getUserSecretKey(),60000,new HashMap<>(Collections.singletonMap("userId",largeId)));
        mvc.perform(get("/user/order/historyOrders").param("page","1").param("pageSize","10").header(jwt.getUserTokenName(),token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].number").value("large-user-order"))
                .andExpect(jsonPath("$.data.records[0].userId").value(largeId));
    }
}
