package com.sky.service.impl;

import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.json.JacksonObjectMapper;
import com.sky.mapper.*;
import com.sky.support.TransactionTestSupport;
import com.sky.vo.OrderSubmitVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionIntegrationTest extends TransactionTestSupport {
    private OrdersServiceImpl orders(OrderDetailMapper details) {
        AddressBookServiceImpl addresses = new AddressBookServiceImpl(); inject(addresses,"addressBookMapper",transactionalMapper(AddressBookMapper.class));
        ShoppingCartServiceImpl cart = new ShoppingCartServiceImpl(); inject(cart,"shoppingCartMapper",transactionalMapper(ShoppingCartMapper.class));
        OrdersServiceImpl target = new OrdersServiceImpl(); inject(target,"ordersMapper",transactionalMapper(OrdersMapper.class));
        inject(target,"orderDetailMapper",details); inject(target,"shoppingCartService",cart); inject(target,"addressBookService",addresses);
        inject(target,"userMapper",transactionalMapper(UserMapper.class)); inject(target,"shoppingCartMapper",transactionalMapper(ShoppingCartMapper.class));
        inject(target,"dishMapper",transactionalMapper(DishMapper.class)); inject(target,"setmealMapper",transactionalMapper(SetmealMapper.class));
        inject(target,"webSocketServer",mock(WebSocketServer.class));
        inject(target,"objectMapper",new JacksonObjectMapper());
        return transactionalService(target,OrdersServiceImpl.class);
    }
    private OrdersSubmitDTO request() {
        OrdersSubmitDTO dto = new OrdersSubmitDTO(); dto.setAddressBookId(1L); dto.setPayMethod(1); dto.setDeliveryStatus(1); dto.setTablewareStatus(1); dto.setTablewareNumber(2); return dto;
    }
    @Test void detailInsertFailureRollsBackOrderAndKeepsCart() {
        OrderDetailMapper details = spy(transactionalMapper(OrderDetailMapper.class));
        doThrow(new IllegalStateException("injected detail failure")).when(details).batchInsert(any());
        assertThatThrownBy(() -> orders(details).submitOrder(request())).hasMessage("injected detail failure");
        assertThat(mapper(OrdersMapper.class).countByStatus(null)).isEqualTo(4);
        assertThat(mapper(ShoppingCartMapper.class).list(ShoppingCart.builder().userId(USER_ID).build())).hasSize(2);
    }
    @Test void cartCleanupFailureRollsBackOrderAndDetails() {
        OrdersServiceImpl target = orders(transactionalMapper(OrderDetailMapper.class));
        com.sky.service.ShoppingCartService cart = mock(com.sky.service.ShoppingCartService.class);
        when(cart.showShoppingCart()).thenReturn(Collections.singletonList(ShoppingCart.builder().dishId(1L).name("鸡丁").amount(new BigDecimal("12.50")).number(2).build()));
        doThrow(new IllegalStateException("injected cleanup failure")).when(cart).cleanShoppingCart();
        // Inject on the proxied target, so the production transaction advice remains active.
        inject(org.springframework.test.util.AopTestUtils.getTargetObject(target),"shoppingCartService",cart);
        assertThatThrownBy(() -> target.submitOrder(request())).hasMessage("injected cleanup failure");
        assertThat(mapper(OrdersMapper.class).countByStatus(null)).isEqualTo(4);
        assertThat(new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject("select count(*) from order_detail",Integer.class)).isEqualTo(5);
    }
    @Test void successfulSubmitPersistsCalculatedAmountAndClearsOnlyCurrentUsersCart() {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.execute(status -> {
            OrdersServiceImpl service = orders(transactionalMapper(OrderDetailMapper.class)); OrderSubmitVO result = service.submitOrder(request());
            Orders saved = transactionalMapper(OrdersMapper.class).getById(result.getId());
            assertThat(saved.getAmount()).isEqualByComparingTo("42.00"); assertThat(saved.getPackAmount()).isEqualTo(3);
            assertThat(transactionalMapper(OrderDetailMapper.class).getByOrderId(result.getId())).hasSize(2);
            assertThat(transactionalMapper(ShoppingCartMapper.class).list(ShoppingCart.builder().userId(USER_ID).build())).isEmpty();
            assertThat(transactionalMapper(ShoppingCartMapper.class).list(ShoppingCart.builder().userId(43L).build())).hasSize(1);
            OrdersPaymentDTO payment = new OrdersPaymentDTO(); payment.setOrderNumber(result.getOrderNumber());
            try { service.payment(payment); } catch (Exception e) { throw new RuntimeException(e); }
            OrdersConfirmDTO confirm = new OrdersConfirmDTO(); confirm.setId(result.getId()); service.confirmOrder(confirm);
            service.deliveryOrder(result.getId()); service.completeOrder(result.getId());
            assertThat(transactionalMapper(OrdersMapper.class).getById(result.getId()).getStatus()).isEqualTo(Orders.COMPLETED);
            status.setRollbackOnly(); return null;
        });
        assertThat(mapper(OrdersMapper.class).countByStatus(null)).isEqualTo(4);
    }
    @Test void failedDefaultAddressUpdateRollsBackPreviousDefaultReset() {
        AddressBookMapper mapper = spy(transactionalMapper(AddressBookMapper.class));
        doThrow(new IllegalStateException("injected default failure")).when(mapper).update(any());
        AddressBookServiceImpl target = new AddressBookServiceImpl(); inject(target,"addressBookMapper",mapper);
        assertThatThrownBy(() -> transactionalService(target,AddressBookServiceImpl.class).setDefault(AddressBook.builder().id(2L).build())).hasMessage("injected default failure");
        assertThat(mapper(AddressBookMapper.class).getById(1L).getIsDefault()).isEqualTo(1);
        assertThat(mapper(AddressBookMapper.class).getById(2L).getIsDefault()).isZero();
    }
    @Test void defaultTargetDeletedAfterOwnershipCheckRollsBackPreviousDefaultReset() {
        AddressBookMapper mapper = spy(transactionalMapper(AddressBookMapper.class));
        doReturn(0).when(mapper).update(any());
        AddressBookServiceImpl target = new AddressBookServiceImpl(); inject(target,"addressBookMapper",mapper);
        assertThatThrownBy(() -> transactionalService(target,AddressBookServiceImpl.class)
                .setDefault(AddressBook.builder().id(2L).build())).isInstanceOf(com.sky.exception.AddressBookBusinessException.class);
        assertThat(mapper(AddressBookMapper.class).getById(1L).getIsDefault()).isEqualTo(1);
        assertThat(mapper(AddressBookMapper.class).getById(2L).getIsDefault()).isZero();
    }
    @Test void failedFlavorInsertRollsBackNewDish() {
        DishFlavorMapper flavors = spy(transactionalMapper(DishFlavorMapper.class)); doThrow(new IllegalStateException("injected flavor failure")).when(flavors).insertBatch(any());
        DishServiceImpl target = new DishServiceImpl(); inject(target,"dishMapper",transactionalMapper(DishMapper.class)); inject(target,"dishFlavorMapper",flavors);
        inject(target,"redisTemplate",mock(RedisTemplate.class));
        DishDTO dto = new DishDTO(); dto.setName("回滚菜品"); dto.setCategoryId(1L); dto.setPrice(BigDecimal.TEN);
        dto.setFlavors(Collections.singletonList(DishFlavor.builder().name("辣度").value("[]").build()));
        assertThatThrownBy(() -> transactionalService(target,DishServiceImpl.class).saveWithFlavor(dto)).hasMessage("injected flavor failure");
        assertThat(mapper(DishMapper.class).countByCategoryId(1L)).isEqualTo(3);
    }
}
