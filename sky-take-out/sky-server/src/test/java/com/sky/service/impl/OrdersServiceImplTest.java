package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.constant.MessageConstant;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.AddressBookService;
import com.sky.service.ShoppingCartService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrdersServiceImplTest {

    @InjectMocks
    private OrdersServiceImpl ordersService;
    @Mock private OrdersMapper ordersMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private ShoppingCartService shoppingCartService;
    @Mock private AddressBookService addressBookService;
    @Mock private UserMapper userMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private WebSocketServer webSocketServer;

    @BeforeEach
    void setCurrentUser() {
        BaseContext.setCurrentId(1L);
    }

    @AfterEach
    void clearCurrentUser() {
        BaseContext.removeCurrentId();
    }

    @Test
    void submitOrderUsesCartPricesInsteadOfClientAmount() {
        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAddressBookId(5L);
        request.setAmount(new BigDecimal("0.01"));
        request.setPackAmount(0);
        request.setTablewareNumber(0);
        when(addressBookService.getById(5L)).thenReturn(AddressBook.builder().id(5L).userId(1L).build());
        when(shoppingCartService.showShoppingCart()).thenReturn(Arrays.asList(
                ShoppingCart.builder().amount(new BigDecimal("12.50")).number(2).build(),
                ShoppingCart.builder().amount(new BigDecimal("8.00")).number(1).build()));
        when(userMapper.getById(1L)).thenReturn(User.builder().name("测试用户").build());

        OrderSubmitVO result = ordersService.submitOrder(request);

        ArgumentCaptor<Orders> order = ArgumentCaptor.forClass(Orders.class);
        verify(ordersMapper).submitOrder(order.capture());
        assertEquals(new BigDecimal("42.00"), order.getValue().getAmount());
        assertEquals(3, order.getValue().getPackAmount());
        assertTrue(order.getValue().getNumber().matches("[0-9a-f]{32}"));
        assertEquals(order.getValue().getNumber(), result.getOrderNumber());
        verify(shoppingCartService).cleanShoppingCart();
    }

    @Test
    void employeeCanQueryOrderDetailWhenEmployeeIdDiffersFromUserId() {
        BaseContext.setCurrentId(100L);
        when(ordersMapper.getById(9L)).thenReturn(Orders.builder().id(9L).userId(2L).build());
        List<OrderDetail> details = Collections.singletonList(
                OrderDetail.builder().orderId(9L).name("测试菜品").number(2).build());
        when(orderDetailMapper.getByOrderId(9L)).thenReturn(details);

        OrderVO result = ordersService.queryOrderDetail(9L);

        assertEquals(9L, result.getId());
        assertEquals(2L, result.getUserId());
        assertEquals(details, result.getOrderDetailList());
    }

    @Test
    void orderOperationsStillRejectMissingOrders() {
        assertEquals(MessageConstant.ORDER_NOT_FOUND,
                assertThrows(OrderBusinessException.class, () -> ordersService.queryOrderDetail(9L)).getMessage());
        assertEquals(MessageConstant.ORDER_NOT_FOUND,
                assertThrows(OrderBusinessException.class, () -> ordersService.userCancelOrder(9L)).getMessage());
        assertEquals(MessageConstant.ORDER_NOT_FOUND,
                assertThrows(OrderBusinessException.class, () -> ordersService.remindOrder(9L)).getMessage());

        verifyNoInteractions(orderDetailMapper, shoppingCartMapper, webSocketServer);
        verify(ordersMapper, never()).update(any(Orders.class), anyInt());
    }

    @Test
    void userCanCancelOrderWithoutCheckingUserId() {
        when(ordersMapper.getById(9L)).thenReturn(Orders.builder()
                .id(9L).userId(2L).status(Orders.PENDING_PAYMENT).build());
        when(ordersMapper.update(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        ordersService.userCancelOrder(9L);

        ArgumentCaptor<Orders> order = ArgumentCaptor.forClass(Orders.class);
        verify(ordersMapper).update(order.capture(), eq(Orders.PENDING_PAYMENT));
        assertEquals(9L, order.getValue().getId());
        assertEquals(Orders.CANCELLED, order.getValue().getStatus());
    }

    @Test
    void repetitionAddsOrderDetailsToCurrentUsersCartWithoutOwnershipCheck() {
        when(orderDetailMapper.getByOrderId(9L)).thenReturn(Collections.singletonList(
                OrderDetail.builder().orderId(9L).dishId(5L).number(2).amount(new BigDecimal("12.50")).build()));

        ordersService.repetition(9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShoppingCart>> cart = ArgumentCaptor.forClass(List.class);
        verify(shoppingCartMapper).batchInsert(cart.capture());
        assertEquals(1, cart.getValue().size());
        assertEquals(1L, cart.getValue().get(0).getUserId());
        assertEquals(5L, cart.getValue().get(0).getDishId());
        assertEquals(2, cart.getValue().get(0).getNumber());
        verifyNoInteractions(ordersMapper);
    }

    @Test
    void userCanRemindOrderWithoutCheckingUserId() {
        when(ordersMapper.getById(9L)).thenReturn(Orders.builder()
                .id(9L).userId(2L).number("order-9").status(Orders.TO_BE_CONFIRMED).build());

        ordersService.remindOrder(9L);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(webSocketServer).sendToAllClient(message.capture());
        assertTrue(message.getValue().contains("order-9"));
    }

    @Test
    void userCanPayOrderWithoutCheckingUserId() throws Exception {
        OrdersPaymentDTO request = new OrdersPaymentDTO();
        request.setOrderNumber("order-9");
        when(ordersMapper.getByNumber("order-9"))
                .thenReturn(Orders.builder().id(9L).userId(2L)
                        .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build());
        when(userMapper.getById(1L)).thenReturn(User.builder().id(1L).build());
        when(ordersMapper.update(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        ordersService.payment(request);

        ArgumentCaptor<Orders> order = ArgumentCaptor.forClass(Orders.class);
        verify(ordersMapper).update(order.capture(), eq(Orders.PENDING_PAYMENT));
        assertEquals(9L, order.getValue().getId());
        assertEquals(Orders.TO_BE_CONFIRMED, order.getValue().getStatus());
        assertEquals(Orders.PAID, order.getValue().getPayStatus());
        verify(webSocketServer).sendToAllClient(anyString());
    }
}
