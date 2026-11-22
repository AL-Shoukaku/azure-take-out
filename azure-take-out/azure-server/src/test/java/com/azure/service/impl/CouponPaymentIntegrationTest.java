package com.azure.service.impl;

import com.azure.cache.CacheClient;
import com.azure.context.BaseContext;
import com.azure.entity.Coupon;
import com.azure.entity.CouponOrder;
import com.azure.entity.UserCoupon;
import com.azure.exception.BaseException;
import com.azure.mapper.CouponMapper;
import com.azure.mapper.CouponOrderMapper;
import com.azure.mapper.UserCouponMapper;
import com.azure.mapper.UserMapper;
import com.azure.message.CouponOrderCreateMessage;
import com.azure.mq.RabbitConstants;
import com.azure.mq.coupon.CouponOrderCreateConsumer;
import com.azure.mq.coupon.CouponOrderCreateProducer;
import com.azure.support.TransactionTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 使用真实 SQL 和事务验证购券支付、发券以及失败回滚。 */
class CouponPaymentIntegrationTest extends TransactionTestSupport {
    private JdbcTemplate jdbc;
    private CouponServiceImpl service;
    private CouponOrderMapper orders;
    private CouponOrderCreateConsumer consumer;
    private RabbitTemplate rabbitTemplate;
    private Long couponId;

    @BeforeEach
    void initializePayment() {
        jdbc = new JdbcTemplate(dataSource);
        orders = transactionalMapper(CouponOrderMapper.class);
        CouponMapper coupons = transactionalMapper(CouponMapper.class);
        CacheClient cacheClient = mock(CacheClient.class);

        rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(2);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(RabbitConstants.COUPON_ORDER_CREATE_QUEUE),
                any(CouponOrderCreateMessage.class), any(CorrelationData.class));
        CouponOrderCreateProducer producer = new CouponOrderCreateProducer();
        inject(producer, "rabbitTemplate", rabbitTemplate);

        RedissonClient redisson = mock(RedissonClient.class);
        when(redisson.getLock(anyString())).thenReturn(mock(RLock.class));
        consumer = new CouponOrderCreateConsumer();
        inject(consumer, "userMapper", transactionalMapper(UserMapper.class));
        inject(consumer, "couponOrderMapper", orders);
        inject(consumer, "couponMapper", coupons);
        inject(consumer, "redissonClient", redisson);
        inject(consumer, "cacheClient", cacheClient);
        inject(consumer, "transactionTemplate", new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        CouponServiceImpl target = new CouponServiceImpl();
        inject(target, "couponOrderMapper", orders);
        inject(target, "userCouponMapper", transactionalMapper(UserCouponMapper.class));
        inject(target, "cacheClient", cacheClient);
        inject(target, "couponOrderCreateProducer", producer);
        service = transactionalService(target, CouponServiceImpl.class);

        LocalDateTime now = LocalDateTime.now();
        Coupon template = new Coupon();
        template.setName("购券时的名称");
        template.setType(Coupon.TYPE_VOUCHER);
        template.setStock(100);
        template.setTotalStock(100);
        template.setPrice(new BigDecimal("9.90"));
        template.setDiscountAmount(new BigDecimal("20.10"));
        template.setThresholdAmount(BigDecimal.ZERO);
        template.setStatus(1);
        template.setStartTime(now.minusDays(1));
        template.setEndTime(now.plusDays(1));
        template.setValidTime(30);
        template.setCreateTime(now);
        template.setUpdateTime(now);
        coupons.insertCoupon(template);
        couponId = jdbc.queryForObject("SELECT id FROM coupon WHERE name = ?", Long.class, template.getName());
        when(cacheClient.getOrLoadCoupon(couponId)).thenAnswer(invocation -> coupons.selectById(couponId));
        when(cacheClient.checkAndDecrStock(eq(couponId), eq(USER_ID), anyString(), eq(Coupon.TYPE_VOUCHER)))
                .thenReturn(0L);
        when(cacheClient.hasCouponReservation(eq(couponId), anyString())).thenReturn(true);
    }

    private CouponOrder purchase() {
        String number = service.userBuyCoupon(couponId).getNumber();
        assertThat(orders.selectByNumberAndUserId(number, USER_ID)).isNull();

        ArgumentCaptor<CouponOrderCreateMessage> message = ArgumentCaptor.forClass(CouponOrderCreateMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConstants.COUPON_ORDER_CREATE_QUEUE),
                message.capture(), any(CorrelationData.class));
        consumer.receiveCouponOrderCreateMessage(message.getValue());

        CouponOrder order = orders.selectByNumberAndUserId(number, USER_ID);
        assertThat(order).isNotNull();
        return order;
    }

    private CouponOrder stored(CouponOrder order) {
        return orders.selectByNumberAndUserId(order.getNumber(), USER_ID);
    }

    private int issuedCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_coupon WHERE user_id = ?", Integer.class, USER_ID);
    }

    @Test
    void paymentIssuesTheReservedCouponFromOrderSnapshotAndPersistsPaymentTime() {
        CouponOrder order = purchase();
        // 购买后的模板变更不应覆盖订单快照，停售也不影响既有订单支付发券。
        jdbc.update("UPDATE coupon SET name = ?, discount_amount = ?, threshold_amount = ?, status = 0 WHERE id = ?",
                "付款时的新名称", new BigDecimal("99.99"), new BigDecimal("100.00"), couponId);

        assertThat(service.payCouponOrder(order.getNumber()).getNumber()).isEqualTo(order.getNumber());

        CouponOrder paid = stored(order);
        assertThat(paid.getStatus()).isEqualTo(CouponOrder.STATUS_PAID);
        assertThat(paid.getPayTime()).isNotNull();
        UserCoupon issued = jdbc.queryForObject("SELECT * FROM user_coupon WHERE number = ?",
                BeanPropertyRowMapper.newInstance(UserCoupon.class), order.getCouponNumber());
        assertThat(issued).isNotNull();
        assertThat(issued.getId()).isNotNull();
        assertThat(issued.getUserId()).isEqualTo(USER_ID);
        assertThat(issued.getCouponId()).isEqualTo(couponId);
        assertThat(issued.getNumber()).isEqualTo(order.getCouponNumber());
        assertThat(issued.getType()).isEqualTo(order.getType());
        assertThat(issued.getName()).isEqualTo(order.getCouponName());
        assertThat(issued.getDiscountAmount()).isEqualByComparingTo(order.getDiscountAmount());
        assertThat(issued.getThresholdAmount()).isEqualByComparingTo(order.getThresholdAmount());
        assertThat(issued.getStatus()).isEqualTo(UserCoupon.STATUS_UNUSED);
        assertThat(issued.getCreateTime()).isEqualTo(paid.getPayTime());
        assertThat(issued.getExpireTime()).isEqualTo(paid.getPayTime().plusDays(30));
        assertThat(issued.getLockOrderId()).isNull();
        assertThat(issued.getUseTime()).isNull();
        assertThat(issued.getRefundTime()).isNull();
        assertThat(jdbc.queryForObject("SELECT stock FROM coupon WHERE id = ?", Integer.class, couponId)).isEqualTo(99);
    }

    @Test
    void repeatedPaymentDoesNotIssueAnotherCouponOrChangePaymentTime() {
        CouponOrder order = purchase();
        service.payCouponOrder(order.getNumber());
        LocalDateTime paidAt = stored(order).getPayTime();

        assertThatThrownBy(() -> service.payCouponOrder(order.getNumber())).isInstanceOf(BaseException.class);

        assertThat(issuedCount()).isEqualTo(1);
        assertThat(stored(order).getPayTime()).isEqualTo(paidAt);
    }

    @Test
    void anotherUsersOrderCannotBePaidOrIssued() {
        CouponOrder order = purchase();
        BaseContext.setCurrentId(43L);

        assertThatThrownBy(() -> service.payCouponOrder(order.getNumber()))
                .isInstanceOf(BaseException.class).hasMessage("代金券订单不存在");

        assertThat(stored(order).getStatus()).isEqualTo(CouponOrder.STATUS_UNPAID);
        assertThat(issuedCount()).isZero();
    }

    @Test
    void overdueOrderCannotBePaidOrIssued() {
        CouponOrder order = purchase();
        jdbc.update("UPDATE coupon_order SET pay_deadline = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), order.getId());

        assertThatThrownBy(() -> service.payCouponOrder(order.getNumber()))
                .isInstanceOf(BaseException.class).hasMessage("代金券订单已超过支付截止时间");

        assertThat(stored(order).getStatus()).isEqualTo(CouponOrder.STATUS_UNPAID);
        assertThat(stored(order).getPayTime()).isNull();
        assertThat(issuedCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void refundedAndClosedOrdersCannotIssueCoupons(int status) {
        CouponOrder order = purchase();
        jdbc.update("UPDATE coupon_order SET status = ? WHERE id = ?", status, order.getId());

        assertThatThrownBy(() -> service.payCouponOrder(order.getNumber())).isInstanceOf(BaseException.class);

        assertThat(stored(order).getStatus()).isEqualTo(status);
        assertThat(issuedCount()).isZero();
    }

    @Test
    void issuanceFailureRollsBackPaidStatusAndPaymentTime() {
        CouponOrder order = purchase();
        // 用重复券号触发真实数据库插入失败，验证支付更新也会回滚。
        jdbc.update("INSERT INTO user_coupon (user_id, coupon_id, number, type, name, discount_amount, threshold_amount, status, expire_time, create_time) " +
                        "VALUES (?, ?, ?, 1, ?, 20.10, 0, 0, ?, ?)",
                43L, couponId, order.getCouponNumber(), "已有券", LocalDateTime.now().plusDays(30), LocalDateTime.now());

        assertThatThrownBy(() -> service.payCouponOrder(order.getNumber())).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(stored(order).getStatus()).isEqualTo(CouponOrder.STATUS_UNPAID);
        assertThat(stored(order).getPayTime()).isNull();
        assertThat(issuedCount()).isZero();
    }
}
