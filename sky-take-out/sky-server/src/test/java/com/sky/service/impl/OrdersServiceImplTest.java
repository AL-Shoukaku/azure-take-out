package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

        ordersService.submitOrder(request);

        ArgumentCaptor<Orders> order = ArgumentCaptor.forClass(Orders.class);
        verify(ordersMapper).submitOrder(order.capture());
        assertEquals(new BigDecimal("42.00"), order.getValue().getAmount());
        assertEquals(3, order.getValue().getPackAmount());
        verify(shoppingCartService).cleanShoppingCart();
    }

    @Test
    void userOperationsRejectAnotherUsersOrder() {
        when(ordersMapper.getById(9L)).thenReturn(Orders.builder().id(9L).userId(2L).build());

        assertThrows(OrderBusinessException.class, () -> ordersService.queryOrderDetail(9L));
        assertThrows(OrderBusinessException.class, () -> ordersService.userCancelOrder(9L));
        assertThrows(OrderBusinessException.class, () -> ordersService.repetition(9L));
        assertThrows(OrderBusinessException.class, () -> ordersService.remindOrder(9L));

        verifyNoInteractions(orderDetailMapper, shoppingCartMapper, webSocketServer);
        verify(ordersMapper, never()).update(any(Orders.class));
    }

    @Test
    void userCannotPayAnotherUsersOrder() {
        OrdersPaymentDTO request = new OrdersPaymentDTO();
        request.setOrderNumber("order-9");
        when(ordersMapper.getByNumber("order-9"))
                .thenReturn(Orders.builder().id(9L).userId(2L).build());

        assertThrows(OrderBusinessException.class, () -> ordersService.payment(request));

        verifyNoInteractions(userMapper);
        verify(ordersMapper, never()).update(any(Orders.class));
    }
}
