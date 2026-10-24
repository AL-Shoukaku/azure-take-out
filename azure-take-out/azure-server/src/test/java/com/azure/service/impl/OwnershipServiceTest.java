package com.azure.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.dto.*;
import com.azure.entity.*;
import com.azure.exception.BaseException;
import com.azure.exception.UserNotLoginException;
import com.azure.context.BaseContext;
import com.azure.json.JacksonObjectMapper;
import com.azure.mapper.*;
import com.azure.service.*;
import com.azure.service.impl.*;
import com.azure.support.ServiceTestSupport;
import com.azure.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ownership regression tests, run by default after the authorization fix. */
class OwnershipServiceTest extends ServiceTestSupport {
    @InjectMocks private OrdersServiceImpl orders;
    @InjectMocks private AddressBookServiceImpl addresses;
    @Mock private OrdersMapper ordersMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private ShoppingCartService shoppingCartService;
    @Mock private AddressBookService addressBookService;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private UserMapper userMapper;
    @Mock private AddressBookMapper addressBookMapper;
    @Mock private WebSocketServer webSocketServer;
    @Spy private ObjectMapper objectMapper = new JacksonObjectMapper();

    @ParameterizedTest @ValueSource(strings={"detail","cancel","remind","payment","repetition"})
    void userCannotOperateAnotherUsersOrder(String operation) {
        // Lenient because a fixed implementation should stop before reading details or writing.
        lenient().when(ordersMapper.getByIdAndUserId(9L,USER_ID)).thenReturn(Orders.builder().id(9L).userId(99L).status(2).payStatus(1).number("foreign-order").build());
        lenient().when(ordersMapper.getByNumberAndUserId("foreign-order",USER_ID)).thenReturn(Orders.builder().id(9L).userId(99L).status(1).payStatus(0).number("foreign-order").build());
        lenient().when(ordersMapper.update(any(),anyInt())).thenReturn(1);
        lenient().when(orderDetailMapper.getByOrderId(9L)).thenReturn(Collections.singletonList(OrderDetail.builder().dishId(7L).number(1).amount(BigDecimal.TEN).build()));
        assertThatThrownBy(() -> {
            switch(operation) {
                case "detail":
                    com.azure.controller.user.OrderController controller = new com.azure.controller.user.OrderController();
                    org.springframework.test.util.ReflectionTestUtils.setField(controller,"ordersService",orders);
                    controller.orderDetail(9L); break;
                case "cancel": orders.userCancelOrder(9L); break;
                case "remind": orders.remindOrder(9L); break;
                case "repetition": orders.repetition(9L); break;
                case "payment": OrdersPaymentDTO dto=new OrdersPaymentDTO(); dto.setOrderNumber("foreign-order"); orders.payment(dto); break;
                default: throw new IllegalArgumentException(operation);
            }
        }).isInstanceOf(BaseException.class);
        verify(ordersMapper,never()).update(any(),anyInt()); verify(ordersMapper,never()).updateForUser(any(),anyInt(),anyLong()); verifyNoInteractions(shoppingCartMapper,webSocketServer);
    }
    @Test void userCannotSubmitUsingAnotherUsersAddress() {
        when(addressBookService.getById(5L)).thenReturn(AddressBook.builder().id(5L).userId(99L).build());
        lenient().when(shoppingCartService.showShoppingCart()).thenReturn(Collections.singletonList(ShoppingCart.builder().dishId(7L).number(1).amount(BigDecimal.TEN).build()));
        lenient().when(userMapper.getById(USER_ID)).thenReturn(User.builder().name("用户").build());
        OrdersSubmitDTO dto=new OrdersSubmitDTO(); dto.setAddressBookId(5L); dto.setTablewareNumber(1);
        assertThatThrownBy(() -> orders.submitOrder(dto)).isInstanceOf(BaseException.class);
        verify(ordersMapper,never()).submitOrder(any());
    }
    @ParameterizedTest @ValueSource(strings={"detail","update","default","delete"})
    void userCannotOperateAnotherUsersAddress(String operation) {
        AddressBook foreign=AddressBook.builder().id(5L).userId(99L).detail("他人地址").build();
        lenient().when(addressBookMapper.getByIdAndUserId(5L,USER_ID)).thenReturn(foreign);
        assertThatThrownBy(() -> {
            switch(operation) {
                case "detail": addresses.getById(5L); break;
                case "update": addresses.update(foreign); break;
                case "default": addresses.setDefault(foreign); break;
                case "delete": addresses.deleteById(5L); break;
                default: throw new IllegalArgumentException(operation);
            }
        }).isInstanceOf(BaseException.class);
        verify(addressBookMapper,never()).updateIsDefaultByUserId(any()); verify(addressBookMapper,never()).update(any()); verify(addressBookMapper,never()).deleteById(anyLong(),anyLong());
    }

    @ParameterizedTest @ValueSource(strings={"detail","cancel","remind","payment","repetition","submit","history"})
    void missingIdentityRejectsUserOperationsBeforeReadingOrWriting(String operation) {
        BaseContext.removeCurrentId();
        assertThatThrownBy(() -> {
            switch(operation) {
                case "detail": orders.queryUserOrderDetail(9L); break;
                case "cancel": orders.userCancelOrder(9L); break;
                case "remind": orders.remindOrder(9L); break;
                case "payment": OrdersPaymentDTO dto=new OrdersPaymentDTO(); dto.setOrderNumber("order-9"); orders.payment(dto); break;
                case "repetition": orders.repetition(9L); break;
                case "submit": orders.submitOrder(new OrdersSubmitDTO()); break;
                case "history": orders.historyOrdersPageQuery(1,10,null); break;
                default: throw new IllegalArgumentException(operation);
            }
        }).isInstanceOf(UserNotLoginException.class);
        verifyNoInteractions(ordersMapper,orderDetailMapper,addressBookService,shoppingCartService,shoppingCartMapper,userMapper,webSocketServer);
    }

    @Test void simulatedPaymentRetainsScopedIdempotencyWhenAnotherPaymentFinishesFirst() throws Exception {
        Orders pending=Orders.builder().id(9L).number("order-9").userId(USER_ID).status(1).payStatus(0).build();
        Orders paid=Orders.builder().id(9L).number("order-9").userId(USER_ID).status(2).payStatus(1).build();
        when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(pending,paid);
        OrdersPaymentDTO request=new OrdersPaymentDTO(); request.setOrderNumber("order-9");
        assertThat(orders.payment(request).getNonceStr()).isEqualTo("fake_nonce_str");
        verify(ordersMapper).updateForUser(any(),eq(1),eq(USER_ID));
        verifyNoInteractions(webSocketServer);
    }

    @Test void simulatedPaymentDoesNotNotifyIfCancellationWinsTheConditionalUpdate() {
        Orders pending=Orders.builder().id(9L).number("order-9").userId(USER_ID).status(1).payStatus(0).build();
        Orders cancelled=Orders.builder().id(9L).number("order-9").userId(USER_ID).status(6).payStatus(0).build();
        when(ordersMapper.getByNumberAndUserId("order-9",USER_ID)).thenReturn(pending,cancelled);
        OrdersPaymentDTO request=new OrdersPaymentDTO(); request.setOrderNumber("order-9");
        assertThatThrownBy(() -> orders.payment(request)).isInstanceOf(BaseException.class).hasMessage("订单状态错误");
        verifyNoInteractions(webSocketServer);
    }
}
