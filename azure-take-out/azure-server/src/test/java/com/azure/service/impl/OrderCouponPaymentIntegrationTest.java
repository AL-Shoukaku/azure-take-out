package com.azure.service.impl;

import com.azure.dto.OrdersPaymentDTO;
import com.azure.entity.Orders;
import com.azure.entity.UserCoupon;
import com.azure.exception.OrderBusinessException;
import com.azure.json.JacksonObjectMapper;
import com.azure.mapper.OrdersMapper;
import com.azure.mapper.UserCouponMapper;
import com.azure.support.TransactionTestSupport;
import com.azure.websocket.WebSocketServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实 SQL 和事务验证外卖订单支付与优惠券核销的一致性。 */
class OrderCouponPaymentIntegrationTest extends TransactionTestSupport {
    private OrdersServiceImpl service;
    private OrdersMapper orders;
    private UserCouponMapper coupons;
    private WebSocketServer notifications;
    private JdbcTemplate jdbc;

    @BeforeEach
    void initializePayment() {
        orders = transactionalMapper(OrdersMapper.class);
        coupons = transactionalMapper(UserCouponMapper.class);
        notifications = mock(WebSocketServer.class);
        jdbc = new JdbcTemplate(dataSource);
        OrdersServiceImpl target = new OrdersServiceImpl();
        inject(target, "ordersMapper", orders);
        inject(target, "userCouponMapper", coupons);
        inject(target, "objectMapper", new JacksonObjectMapper());
        inject(target, "webSocketServer", notifications);
        service = transactionalService(target, OrdersServiceImpl.class);
    }

    private OrdersPaymentDTO request() {
        OrdersPaymentDTO request = new OrdersPaymentDTO();
        request.setOrderNumber("order-1");
        request.setPayMethod(1);
        return request;
    }

    private UserCoupon lockCoupon(long couponId, long orderId) {
        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(USER_ID);
        coupon.setCouponId(couponId);
        coupon.setNumber("ORDER-PAYMENT-COUPON-" + couponId);
        coupon.setType(1);
        coupon.setName("订单支付测试券");
        coupon.setDiscountAmount(new BigDecimal("10.00"));
        coupon.setThresholdAmount(BigDecimal.ZERO);
        coupon.setStatus(UserCoupon.STATUS_LOCKED);
        coupon.setLockOrderId(orderId);
        coupon.setCreateTime(LocalDateTime.now().minusDays(1));
        coupon.setExpireTime(LocalDateTime.now().plusDays(1));
        coupons.insertUserCoupon(coupon);
        return coupon;
    }

    private void assertCouponStatus(UserCoupon coupon, int status) {
        UserCoupon saved = coupons.getByCouponNumber(coupon.getNumber());
        assertThat(saved.getStatus()).isEqualTo(status);
        assertThat(saved.getLockOrderId()).isEqualTo(coupon.getLockOrderId());
        if (status == UserCoupon.STATUS_USED) {
            assertThat(saved.getUseTime()).isNotNull();
        } else {
            assertThat(saved.getUseTime()).isNull();
        }
    }

    @Test
    void successfulPaymentUsesOnlyCouponsLockedByThePaidOrder() throws Exception {
        jdbc.update("UPDATE orders SET coupon_count = 2 WHERE id = 1");
        UserCoupon first = lockCoupon(11, 1);
        UserCoupon second = lockCoupon(12, 1);
        UserCoupon anotherOrderCoupon = lockCoupon(13, 2);

        service.payment(request());

        Orders paid = orders.getById(1L);
        assertThat(paid.getStatus()).isEqualTo(Orders.TO_BE_CONFIRMED);
        assertThat(paid.getPayStatus()).isEqualTo(Orders.PAID);
        assertThat(paid.getCheckoutTime()).isNotNull();
        assertCouponStatus(first, UserCoupon.STATUS_USED);
        assertCouponStatus(second, UserCoupon.STATUS_USED);
        assertCouponStatus(anotherOrderCoupon, UserCoupon.STATUS_LOCKED);
        verify(notifications).sendToAllClient(anyString());
    }

    @Test
    void couponCountMismatchRollsBackOrderAndAlreadyUpdatedCoupons() {
        jdbc.update("UPDATE orders SET coupon_count = 2 WHERE id = 1");
        UserCoupon coupon = lockCoupon(11, 1);

        assertThatThrownBy(() -> service.payment(request()))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessage("存在优惠券的状态不为锁定中，无法完成支付，请联系管理员");

        Orders unpaid = orders.getById(1L);
        assertThat(unpaid.getStatus()).isEqualTo(Orders.PENDING_PAYMENT);
        assertThat(unpaid.getPayStatus()).isEqualTo(Orders.UN_PAID);
        assertThat(unpaid.getCheckoutTime()).isNull();
        assertCouponStatus(coupon, UserCoupon.STATUS_LOCKED);
        verifyNoInteractions(notifications);
    }

    @Test
    void paymentWithoutCouponsStillSucceeds() throws Exception {
        UserCoupon anotherOrderCoupon = lockCoupon(11, 2);

        service.payment(request());

        assertThat(orders.getById(1L).getPayStatus()).isEqualTo(Orders.PAID);
        assertCouponStatus(anotherOrderCoupon, UserCoupon.STATUS_LOCKED);
        verify(notifications).sendToAllClient(anyString());
    }
}
