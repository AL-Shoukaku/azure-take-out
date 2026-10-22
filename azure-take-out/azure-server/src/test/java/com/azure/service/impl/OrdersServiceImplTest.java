package com.azure.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.constant.MessageConstant;
import com.azure.dto.*;
import com.azure.entity.*;
import com.azure.exception.*;
import com.azure.json.JacksonObjectMapper;
import com.azure.mapper.*;
import com.azure.result.PageResult;
import com.azure.service.*;
import com.azure.support.ServiceTestSupport;
import com.azure.vo.*;
import com.azure.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrdersServiceImplTest extends ServiceTestSupport {
    @InjectMocks private OrdersServiceImpl service;
    @Mock private OrdersMapper ordersMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private ShoppingCartService shoppingCartService;
    @Mock private AddressBookService addressBookService;
    @Mock private UserMapper userMapper;
    @Mock private DishMapper dishMapper;
    @Mock private SetmealMapper setmealMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private WebSocketServer webSocketServer;
    @Spy private ObjectMapper objectMapper = new JacksonObjectMapper();
    @Captor private ArgumentCaptor<Orders> order;
    @Captor private ArgumentCaptor<List<OrderDetail>> details;
    @Captor private ArgumentCaptor<List<ShoppingCart>> cart;
    @Captor private ArgumentCaptor<String> notification;

    private OrdersSubmitDTO submitRequest() {
        OrdersSubmitDTO dto = new OrdersSubmitDTO(); dto.setAddressBookId(5L); dto.setAmount(new BigDecimal("0.01"));
        dto.setPackAmount(-100); dto.setTablewareNumber(2); dto.setTablewareStatus(1); dto.setPayMethod(1);
        dto.setDeliveryStatus(1); dto.setEstimatedDeliveryTime(LocalDateTime.of(2026,10,8,12,30,45)); dto.setRemark("少盐"); return dto;
    }
    private AddressBook address() {
        return AddressBook.builder().id(5L).userId(USER_ID).phone("13800000000").consignee("张三").detail("测试路1号").build();
    }
    private Orders existing(int status) {
        return Orders.builder().id(9L).number("order-9").userId(USER_ID).status(status)
                .payStatus(status == Orders.PENDING_PAYMENT ? Orders.UN_PAID : Orders.PAID).build();
    }
    private OrdersPaymentDTO paymentRequest() {
        OrdersPaymentDTO dto = new OrdersPaymentDTO(); dto.setOrderNumber("order-9"); dto.setPayMethod(1); return dto;
    }

    private void assertNotification(int expectedType) throws Exception {
        verify(webSocketServer).sendToAllClient(notification.capture());
        JsonNode payload = objectMapper.readTree(notification.getValue());
        assertThat(payload.isObject()).isTrue();
        assertThat(payload.size()).isEqualTo(3);
        assertThat(payload.path("type").isIntegralNumber()).isTrue();
        assertThat(payload.path("type").intValue()).isEqualTo(expectedType);
        assertThat(payload.path("orderId").isIntegralNumber()).isTrue();
        assertThat(payload.path("orderId").longValue()).isEqualTo(9L);
        assertThat(payload.path("content").isTextual()).isTrue();
        assertThat(payload.path("content").textValue()).isEqualTo("订单号 order-9");
    }
    @Test void submitComputesAmountFromUnitPricesQuantityDeliveryAndPackaging() {
        when(addressBookService.getById(5L)).thenReturn(address());
        when(shoppingCartService.showShoppingCart()).thenReturn(Arrays.asList(
                ShoppingCart.builder().id(100L).dishId(7L).name("鸡丁").image("dish.png").dishFlavor("微辣").amount(new BigDecimal("12.50")).number(2).build(),
                ShoppingCart.builder().id(101L).setmealId(8L).name("套餐").amount(new BigDecimal("8.00")).number(1).build()));
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().id(7L).status(1).build());
        when(setmealMapper.getById(8L)).thenReturn(Setmeal.builder().id(8L).status(1).build());
        when(userMapper.getById(USER_ID)).thenReturn(User.builder().name("测试用户").build());
        doAnswer(i -> { ((Orders)i.getArgument(0)).setId(99L); return null; }).when(ordersMapper).submitOrder(any());
        LocalDateTime before = LocalDateTime.now(); OrderSubmitVO result = service.submitOrder(submitRequest());
        verify(ordersMapper).submitOrder(order.capture()); Orders saved = order.getValue();
        assertThat(saved.getAmount()).isEqualByComparingTo("42.00"); assertThat(saved.getPackAmount()).isEqualTo(3);
        assertThat(saved.getNumber()).matches("[0-9a-f]{32}"); assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getOrderTime()).isBetween(before,LocalDateTime.now());
        assertThat(saved).extracting(Orders::getStatus,Orders::getPayStatus,Orders::getPhone,Orders::getConsignee,
                Orders::getAddress,Orders::getUserName,Orders::getRemark,Orders::getTablewareNumber,Orders::getTablewareStatus)
                .containsExactly(Orders.PENDING_PAYMENT,Orders.UN_PAID,"13800000000","张三","测试路1号","测试用户","少盐",2,1);
        assertThat(saved.getEstimatedDeliveryTime()).isEqualTo(submitRequest().getEstimatedDeliveryTime());
        verify(orderDetailMapper).batchInsert(details.capture());
        assertThat(details.getValue()).extracting(OrderDetail::getOrderId).containsExactly(99L,99L);
        assertThat(details.getValue().get(0)).extracting(OrderDetail::getDishId,OrderDetail::getDishFlavor,OrderDetail::getNumber,OrderDetail::getName,OrderDetail::getImage)
                .containsExactly(7L,"微辣",2,"鸡丁","dish.png");
        assertThat(details.getValue().get(0).getAmount()).isEqualByComparingTo("12.50");
        assertThat(details.getValue().get(1).getSetmealId()).isEqualTo(8L);
        assertThat(result.getId()).isEqualTo(99L); assertThat(result.getOrderNumber()).isEqualTo(saved.getNumber());
        assertThat(result.getOrderAmount()).isEqualByComparingTo("42.00"); assertThat(result.getOrderTime()).isEqualTo(saved.getOrderTime());
        InOrder sequence = inOrder(ordersMapper,orderDetailMapper,shoppingCartService);
        sequence.verify(shoppingCartService).showShoppingCart(); sequence.verify(ordersMapper).submitOrder(any());
        sequence.verify(orderDetailMapper).batchInsert(any()); sequence.verify(shoppingCartService).cleanShoppingCart();
        verifyNoInteractions(webSocketServer);
    }
    @Test void missingAddressRejectsBeforeReadingCartOrWriting() {
        assertThatThrownBy(() -> service.submitOrder(submitRequest())).isInstanceOf(AddressBookBusinessException.class)
                .hasMessage(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        verifyNoInteractions(shoppingCartService,ordersMapper,orderDetailMapper);
    }
    @ParameterizedTest @NullAndEmptySource
    void emptyCartRejectsOrder(List<ShoppingCart> items) {
        when(addressBookService.getById(5L)).thenReturn(address()); when(shoppingCartService.showShoppingCart()).thenReturn(items);
        assertThatThrownBy(() -> service.submitOrder(submitRequest())).isInstanceOf(ShoppingCartBusinessException.class)
                .hasMessage(MessageConstant.SHOPPING_CART_IS_NULL);
        verifyNoInteractions(ordersMapper,orderDetailMapper,userMapper); verify(shoppingCartService,never()).cleanShoppingCart();
    }
    @Test void detailWriteFailureDoesNotClearCart() {
        when(addressBookService.getById(5L)).thenReturn(address());
        when(shoppingCartService.showShoppingCart()).thenReturn(Collections.singletonList(ShoppingCart.builder().dishId(7L).amount(BigDecimal.TEN).number(1).build()));
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().id(7L).status(1).build());
        when(userMapper.getById(USER_ID)).thenReturn(User.builder().name("用户").build());
        doThrow(new IllegalStateException("detail insert failed")).when(orderDetailMapper).batchInsert(any());
        assertThatThrownBy(() -> service.submitOrder(submitRequest())).hasMessage("detail insert failed");
        verify(shoppingCartService,never()).cleanShoppingCart();
    }
    private static Stream<Arguments> unavailableProducts() {
        return Stream.of("dish","setmeal").flatMap(kind -> Stream.of("stopped","missing")
                .map(state -> Arguments.of(kind,state)));
    }
    @ParameterizedTest @MethodSource("unavailableProducts")
    void unavailableCartProductRejectsBeforeAnyOrderWriteOrCartCleanup(String kind,String state) {
        when(addressBookService.getById(5L)).thenReturn(address());
        ShoppingCart item=ShoppingCart.builder().amount(BigDecimal.TEN).number(1).build();
        Integer status=0;
        if (kind.equals("dish")) {
            item.setDishId(7L);
            when(dishMapper.getById(7L)).thenReturn(state.equals("missing") ? null : Dish.builder().id(7L).status(status).build());
        } else {
            item.setSetmealId(8L);
            when(setmealMapper.getById(8L)).thenReturn(state.equals("missing") ? null : Setmeal.builder().id(8L).status(status).build());
        }
        when(shoppingCartService.showShoppingCart()).thenReturn(Collections.singletonList(item));
        assertThatThrownBy(() -> service.submitOrder(submitRequest())).isInstanceOf(ShoppingCartBusinessException.class)
                .hasMessage(MessageConstant.SHOPPING_CART_PRODUCT_UNAVAILABLE);
        verifyNoInteractions(ordersMapper,orderDetailMapper,userMapper);
        verify(shoppingCartService,never()).cleanShoppingCart();
    }
    @Test void simulatedPaymentReturnsCompatibleFieldsWithoutLoadingUser() throws Exception {
        when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(existing(Orders.PENDING_PAYMENT));
        when(ordersMapper.updateForUser(any(),eq(Orders.PENDING_PAYMENT),eq(USER_ID))).thenReturn(1);
        OrderPaymentVO result = service.payment(paymentRequest());
        assertThat(result.getNonceStr()).isEqualTo("fake_nonce_str"); assertThat(result.getPaySign()).isEqualTo("fake_pay_sign");
        assertThat(result.getSignType()).isEqualTo("RSA"); assertThat(result.getPackageStr()).isEqualTo("prepay_id=fake_prepay_id");
        assertThat(result.getTimeStamp()).matches("\\d+"); verifyNoInteractions(userMapper);
        verify(webSocketServer).sendToAllClient(anyString());
    }
    @Test void paymentRejectsMissingOrder() {
        assertThatThrownBy(() -> service.payment(paymentRequest())).isInstanceOf(OrderBusinessException.class).hasMessage(MessageConstant.ORDER_NOT_FOUND);
        verifyNoInteractions(userMapper,webSocketServer);
    }
    @ParameterizedTest @CsvSource({"2,1","3,1","4,1","5,1","6,0","1,1","1,2"})
    void paymentRejectsInvalidOrderOrPaymentState(int status,int payStatus) {
        Orders row = existing(status); row.setPayStatus(payStatus); when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(row);
        assertThatThrownBy(() -> service.payment(paymentRequest())).isInstanceOf(OrderBusinessException.class).hasMessage(MessageConstant.ORDER_STATUS_ERROR);
        verify(ordersMapper,never()).updateForUser(any(),anyInt(),anyLong()); verifyNoInteractions(webSocketServer);
    }
    @Test void successfulPaymentTransitionsOnceAndSendsStructuredNotification() throws Exception {
        when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(existing(Orders.PENDING_PAYMENT));
        when(ordersMapper.updateForUser(any(),eq(Orders.PENDING_PAYMENT),eq(USER_ID))).thenReturn(1);
        LocalDateTime before = LocalDateTime.now(); service.payment(paymentRequest());
        verify(ordersMapper).updateForUser(order.capture(),eq(Orders.PENDING_PAYMENT),eq(USER_ID));
        assertThat(order.getValue()).extracting(Orders::getId,Orders::getStatus,Orders::getPayStatus)
                .containsExactly(9L,Orders.TO_BE_CONFIRMED,Orders.PAID);
        assertThat(order.getValue().getCheckoutTime()).isBetween(before,LocalDateTime.now());
        assertNotification(1);
    }

    @Test
    void paymentSerializationFailureDoesNotUpdateOrderOrNotify() throws Exception {
        when(ordersMapper.getByNumberAndUserId("order-9", USER_ID))
                .thenReturn(existing(Orders.PENDING_PAYMENT));
        JsonProcessingException failure = new JsonProcessingException("synthetic JSON failure") {};
        doThrow(failure).when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> service.payment(paymentRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("订单通知 JSON 序列化失败")
                .hasCause(failure);

        verify(ordersMapper, never()).updateForUser(any(), anyInt(), anyLong());
        verifyNoInteractions(webSocketServer);
    }
    @ParameterizedTest @ValueSource(ints = {2,3,4,5,6})
    void unpaidNonPendingOrderRejectsSimulatedPayment(int status) {
        Orders row = existing(status); row.setPayStatus(Orders.UN_PAID);
        when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(row);
        assertThatThrownBy(() -> service.payment(paymentRequest())).hasMessage(MessageConstant.ORDER_STATUS_ERROR);
        verify(ordersMapper,never()).updateForUser(any(),anyInt(),anyLong()); verifyNoInteractions(webSocketServer);
    }
    @ParameterizedTest @ValueSource(ints = {1,2})
    void concurrentPaymentAlreadyHandledDoesNotNotifyAgain(int payStatus) throws Exception {
        Orders latest = existing(Orders.TO_BE_CONFIRMED); latest.setPayStatus(payStatus);
        when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(existing(Orders.PENDING_PAYMENT),latest);
        when(ordersMapper.updateForUser(any(),eq(Orders.PENDING_PAYMENT),eq(USER_ID))).thenReturn(0);
        service.payment(paymentRequest());
        verify(ordersMapper,times(2)).getByNumberAndUserId("order-9",USER_ID); verifyNoInteractions(webSocketServer);
    }
    @Test void concurrentCancellationRejectsPaymentWithoutNotification() {
        Orders cancelled = existing(Orders.CANCELLED); cancelled.setPayStatus(Orders.UN_PAID);
        when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(existing(Orders.PENDING_PAYMENT),cancelled);
        when(ordersMapper.updateForUser(any(),eq(Orders.PENDING_PAYMENT),eq(USER_ID))).thenReturn(0);
        assertThatThrownBy(() -> service.payment(paymentRequest())).hasMessage(MessageConstant.ORDER_STATUS_ERROR); verifyNoInteractions(webSocketServer);
    }
    @Test void deletedOrderDuringPaymentRejectsWithoutNotification() {
        when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(existing(Orders.PENDING_PAYMENT),null);
        when(ordersMapper.updateForUser(any(),eq(Orders.PENDING_PAYMENT),eq(USER_ID))).thenReturn(0);
        assertThatThrownBy(() -> service.payment(paymentRequest())).hasMessage(MessageConstant.ORDER_STATUS_ERROR); verifyNoInteractions(webSocketServer);
    }

    // Matrix covers every accepted transition, every invalid source state, and lost updates.
    private static Stream<Arguments> transitions() {
        return Stream.of(Arguments.of("confirm",2,3),Arguments.of("reject",2,6),Arguments.of("delivery",3,4),
                Arguments.of("complete",4,5),Arguments.of("userCancel",1,6),Arguments.of("userCancel",2,6),
                Arguments.of("adminCancel",1,6),Arguments.of("adminCancel",3,6),
                Arguments.of("adminCancel",4,6),Arguments.of("adminCancel",5,6));
    }
    private void transition(String action) {
        switch (action) {
            case "confirm": OrdersConfirmDTO confirm = new OrdersConfirmDTO(); confirm.setId(9L); service.confirmOrder(confirm); break;
            case "reject": OrdersRejectionDTO reject = new OrdersRejectionDTO(); reject.setId(9L); reject.setRejectionReason(" 售罄 "); service.rejectOrder(reject); break;
            case "delivery": service.deliveryOrder(9L); break;
            case "complete": service.completeOrder(9L); break;
            case "userCancel": service.userCancelOrder(9L); break;
            case "adminCancel": OrdersCancelDTO cancel = new OrdersCancelDTO(); cancel.setId(9L); cancel.setCancelReason(" 停业 "); service.cancelOrder(cancel); break;
            case "remind": service.remindOrder(9L); break;
            case "detail": service.queryUserOrderDetail(9L); break;
            default: throw new IllegalArgumentException(action);
        }
    }
    private boolean userOperation(String action) {
        return action.equals("userCancel") || action.equals("remind") || action.equals("detail");
    }
    private void stubOrder(String action,int source) {
        if (userOperation(action)) when(ordersMapper.getByIdAndUserId(9L,USER_ID)).thenReturn(existing(source));
        else when(ordersMapper.getById(9L)).thenReturn(existing(source));
    }
    private void stubTransition(String action,int source,int affected) {
        stubOrder(action,source);
        if (action.equals("userCancel")) when(ordersMapper.updateForUser(any(),eq(source),eq(USER_ID))).thenReturn(affected);
        else when(ordersMapper.update(any(),eq(source))).thenReturn(affected);
    }
    private void captureTransition(String action,int source) {
        if (action.equals("userCancel")) verify(ordersMapper).updateForUser(order.capture(),eq(source),eq(USER_ID));
        else verify(ordersMapper).update(order.capture(),eq(source));
    }
    @ParameterizedTest @MethodSource("transitions")
    void validTransitionUsesExpectedSourceState(String action,int source,int target) {
        stubTransition(action,source,1);
        LocalDateTime before = LocalDateTime.now();
        transition(action); captureTransition(action,source);
        assertThat(order.getValue().getId()).isEqualTo(9L); assertThat(order.getValue().getStatus()).isEqualTo(target);
        if (target == Orders.CANCELLED) {
            assertThat(order.getValue().getCancelTime()).isBetween(before,LocalDateTime.now());
        }
        if (action.equals("reject")) { assertThat(order.getValue().getRejectionReason()).isEqualTo("售罄"); assertThat(order.getValue().getPayStatus()).isEqualTo(Orders.REFUND); }
        if (action.equals("adminCancel")) assertThat(order.getValue().getCancelReason()).isEqualTo("停业");
        if (action.equals("userCancel")) assertThat(order.getValue().getCancelReason()).isEqualTo("用户在接单前取消");
        if (action.endsWith("Cancel")) {
            assertThat(order.getValue().getPayStatus())
                    .isEqualTo(source == Orders.PENDING_PAYMENT ? null : Orders.REFUND);
        }
        verifyNoInteractions(webSocketServer);
    }
    @ParameterizedTest @MethodSource("transitions")
    void lostConcurrentUpdateRejectsTransition(String action,int source,int target) {
        stubTransition(action,source,0);
        assertThatThrownBy(() -> transition(action)).isInstanceOf(OrderBusinessException.class).hasMessage(MessageConstant.ORDER_STATUS_ERROR);
    }
    private static Stream<Arguments> invalidTransitions() {
        return Stream.of("confirm","reject","delivery","complete","userCancel","adminCancel","remind").flatMap(action ->
                Stream.of(1,2,3,4,5,6).filter(status -> {
                    if (action.equals("userCancel")) return status != 1 && status != 2;
                    if (action.equals("adminCancel"))
                        return status != 1 && status != 3 && status != 4 && status != 5;
                    int allowed = action.equals("delivery") ? 3 : action.equals("complete") ? 4 : 2;
                    return status != allowed;
                }).map(status -> Arguments.of(action,status)));
    }
    @ParameterizedTest @MethodSource("invalidTransitions")
    void invalidSourceStatusDoesNotWriteOrNotify(String action,int source) {
        stubOrder(action,source);
        assertThatThrownBy(() -> transition(action)).isInstanceOf(OrderBusinessException.class).hasMessage(MessageConstant.ORDER_STATUS_ERROR);
        verify(ordersMapper,never()).update(any(),anyInt()); verifyNoInteractions(webSocketServer);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   "})
    void adminCancelRequiresNonBlankReason(String reason) {
        when(ordersMapper.getById(9L)).thenReturn(existing(Orders.CONFIRMED));
        OrdersCancelDTO cancel = new OrdersCancelDTO(); cancel.setId(9L); cancel.setCancelReason(reason);
        assertThatThrownBy(() -> service.cancelOrder(cancel))
                .hasMessage(MessageConstant.ORDER_CANCEL_REASON_REQUIRED);
        verify(ordersMapper,never()).update(any(),anyInt());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   "})
    void rejectRequiresNonBlankReason(String reason) {
        when(ordersMapper.getById(9L)).thenReturn(existing(Orders.TO_BE_CONFIRMED));
        OrdersRejectionDTO reject = new OrdersRejectionDTO(); reject.setId(9L); reject.setRejectionReason(reason);
        assertThatThrownBy(() -> service.rejectOrder(reject))
                .hasMessage(MessageConstant.ORDER_CANCEL_REASON_REQUIRED);
        verify(ordersMapper,never()).update(any(),anyInt());
    }

    @ParameterizedTest @ValueSource(strings = {"confirm","reject","delivery","complete","userCancel","adminCancel","remind","detail"})
    void missingOrderRejectedForEveryOperation(String action) {
        assertThatThrownBy(() -> transition(action)).isInstanceOf(OrderBusinessException.class).hasMessage(MessageConstant.ORDER_NOT_FOUND);
        verify(ordersMapper,never()).update(any(),anyInt()); verifyNoInteractions(orderDetailMapper,webSocketServer);
    }
    @Test void reminderNotificationHasTypeTwo() throws Exception {
        when(ordersMapper.getByIdAndUserId(9L,USER_ID)).thenReturn(existing(2)); service.remindOrder(9L);
        assertNotification(2); verify(ordersMapper,never()).update(any(),anyInt());
    }

    @Test
    void reminderSerializationFailureDoesNotUpdateOrderOrNotify() throws Exception {
        when(ordersMapper.getByIdAndUserId(9L, USER_ID))
                .thenReturn(existing(Orders.TO_BE_CONFIRMED));
        JsonProcessingException failure = new JsonProcessingException("synthetic JSON failure") {};
        doThrow(failure).when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> service.remindOrder(9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("订单通知 JSON 序列化失败")
                .hasCause(failure);

        verify(ordersMapper, never()).update(any(), anyInt());
        verifyNoInteractions(webSocketServer);
    }
    @Test void detailCombinesOrderAndItems() {
        when(ordersMapper.getById(9L)).thenReturn(existing(2));
        OrderDetail row = OrderDetail.builder().orderId(9L).dishId(7L).number(2).build();
        when(orderDetailMapper.getByOrderId(9L)).thenReturn(Collections.singletonList(row));
        OrderVO result = service.queryOrderDetail(9L); assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getNumber()).isEqualTo("order-9"); assertThat(result.getOrderDetailList()).containsExactly(row);
    }
    @Test void administratorCanViewOrderOwnedByDifferentUser() {
        com.azure.context.BaseContext.setCurrentId(7L);
        when(ordersMapper.getById(9L)).thenReturn(existing(2));
        when(orderDetailMapper.getByOrderId(9L)).thenReturn(Collections.emptyList());
        com.azure.controller.admin.OrderController controller = new com.azure.controller.admin.OrderController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"ordersService",service);
        assertThat(controller.queryOrderDetail(9L).getData().getUserId()).isEqualTo(USER_ID);
    }
    @Test void historyIsScopedToCurrentUserAndIncludesDetails() {
        Orders row = existing(2); when(ordersMapper.pageQuery(USER_ID,2)).thenReturn(page(18,row));
        OrderDetail item = OrderDetail.builder().orderId(9L).name("鸡丁").number(2).build();
        when(orderDetailMapper.getByOrderId(9L)).thenReturn(Collections.singletonList(item));
        PageResult result = service.historyOrdersPageQuery(2,5,2);
        assertThat(result.getTotal()).isEqualTo(18); assertThat(result.getRecords()).hasSize(1);
        assertThat(((OrderVO)result.getRecords().get(0)).getOrderDetailList()).containsExactly(item);
    }
    @Test void emptyHistorySupportsNoStatusFilter() {
        when(ordersMapper.pageQuery(USER_ID,null)).thenReturn(page(0));
        PageResult result = service.historyOrdersPageQuery(1,10,null); assertThat(result.getTotal()).isZero(); assertThat(result.getRecords()).isEmpty();
        verifyNoInteractions(orderDetailMapper);
    }
    @Test void adminSearchFormatsDishNamesAndQuantities() {
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO(); dto.setPage(1); dto.setPageSize(10);
        when(ordersMapper.adminPageSearch(dto)).thenReturn(page(19,existing(2)));
        when(orderDetailMapper.getByOrderId(9L)).thenReturn(Arrays.asList(OrderDetail.builder().name("鸡丁").number(2).build(),OrderDetail.builder().name("米饭").number(1).build()));
        PageResult result = service.adminPageQuery(dto); assertThat(result.getTotal()).isEqualTo(19);
        assertThat(((OrderVO)result.getRecords().get(0)).getOrderDishes()).isEqualTo("鸡丁*2;米饭*1;");
    }
    @Test void emptyAdminSearchReturnsEmptyPage() {
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO(); dto.setPage(1); dto.setPageSize(10);
        when(ordersMapper.adminPageSearch(dto)).thenReturn(page(0)); assertThat(service.adminPageQuery(dto).getRecords()).isEmpty();
        verifyNoInteractions(orderDetailMapper);
    }
    @Test void repetitionUsesCurrentProductDetailsAndPreservesHistoricalFlavorAndQuantity() {
        when(ordersMapper.getByIdAndUserId(9L,USER_ID)).thenReturn(existing(Orders.COMPLETED));
        when(orderDetailMapper.getByOrderId(9L)).thenReturn(Arrays.asList(
                OrderDetail.builder().dishId(7L).name("鸡丁").dishFlavor("微辣").amount(new BigDecimal("12.50")).number(2).build(),
                OrderDetail.builder().setmealId(8L).name("套餐").amount(new BigDecimal("29.90")).number(1).build()));
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().id(7L).status(1).name("新版鸡丁")
                .image("new-dish.png").price(new BigDecimal("30.00")).build());
        when(setmealMapper.getById(8L)).thenReturn(Setmeal.builder().id(8L).status(1).name("新版套餐")
                .image("new-setmeal.png").price(new BigDecimal("60.00")).build());
        LocalDateTime before = LocalDateTime.now(); service.repetition(9L); verify(shoppingCartMapper).batchInsert(cart.capture());
        assertThat(cart.getValue()).extracting(ShoppingCart::getUserId).containsExactly(USER_ID,USER_ID);
        assertThat(cart.getValue().get(0)).extracting(ShoppingCart::getDishId,ShoppingCart::getDishFlavor,ShoppingCart::getNumber)
                .containsExactly(7L,"微辣",2);
        assertThat(cart.getValue().get(0).getAmount()).isEqualByComparingTo("30.00");
        assertThat(cart.getValue().get(0)).extracting(ShoppingCart::getName,ShoppingCart::getImage)
                .containsExactly("新版鸡丁","new-dish.png");
        assertThat(cart.getValue().get(1).getSetmealId()).isEqualTo(8L);
        assertThat(cart.getValue().get(1).getAmount()).isEqualByComparingTo("60.00");
        assertThat(cart.getValue().get(1)).extracting(ShoppingCart::getName,ShoppingCart::getImage,
                ShoppingCart::getNumber,ShoppingCart::getDishFlavor).containsExactly("新版套餐","new-setmeal.png",1,null);
        assertThat(cart.getValue().get(0).getCreateTime()).isBetween(before,LocalDateTime.now());
        verify(shoppingCartMapper,never()).insert(any());
    }
    @Test void statisticsMapsThreeActiveStatuses() {
        when(ordersMapper.countByStatus(2)).thenReturn(4); when(ordersMapper.countByStatus(3)).thenReturn(5); when(ordersMapper.countByStatus(4)).thenReturn(6);
        OrderStatisticsVO result = service.statistics(); assertThat(result).extracting(OrderStatisticsVO::getToBeConfirmed,OrderStatisticsVO::getConfirmed,OrderStatisticsVO::getDeliveryInProgress)
                .containsExactly(4,5,6);
    }
}
