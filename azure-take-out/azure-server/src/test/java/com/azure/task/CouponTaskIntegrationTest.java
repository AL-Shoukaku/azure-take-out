package com.azure.task;

import com.azure.entity.CouponOrder;
import com.azure.mapper.CouponMapper;
import com.azure.mapper.CouponOrderMapper;
import com.azure.support.TransactionTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实 SQL 和事务验证超时关闭购券订单与恢复库存。 */
class CouponTaskIntegrationTest extends TransactionTestSupport {
    private JdbcTemplate jdbc;
    private CouponOrderMapper orders;
    private CouponMapper coupons;
    private CouponTask task;

    @BeforeEach
    void initializeTask() {
        jdbc = new JdbcTemplate(dataSource);
        orders = transactionalMapper(CouponOrderMapper.class);
        coupons = transactionalMapper(CouponMapper.class);
        task = taskWith(orders, coupons);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("INSERT INTO coupon (id, type, name, total_stock, stock, discount_amount, threshold_amount, price, " +
                        "start_time, end_time, status, valid_time, create_time, update_time) " +
                        "VALUES (1, 1, '超时退库存测试券', 100, 96, 20, 0, 9.9, ?, ?, 1, 30, ?, ?)",
                now.minusDays(1), now.plusDays(1), now, now);
    }

    private CouponTask taskWith(CouponOrderMapper orderMapper, CouponMapper couponMapper) {
        CouponTask target = new CouponTask();
        inject(target, "couponOrderMapper", orderMapper);
        inject(target, "couponMapper", couponMapper);
        return transactionalService(target, CouponTask.class);
    }

    private CouponOrder order(String number, int status, LocalDateTime deadline) {
        CouponOrder order = new CouponOrder();
        order.setUserId(USER_ID);
        order.setCouponId(1L);
        order.setNumber(number);
        order.setCouponNumber(number + "-COUPON");
        order.setUsername("测试用户");
        order.setCouponName("超时退库存测试券");
        order.setType(1);
        order.setPrice(new BigDecimal("9.90"));
        order.setDiscountAmount(new BigDecimal("20.00"));
        order.setThresholdAmount(BigDecimal.ZERO);
        order.setStatus(status);
        order.setPayDeadline(deadline);
        order.setCreateTime(deadline.minusMinutes(15));
        orders.insertCouponOrder(order);
        return order;
    }

    private int stock() {
        return coupons.selectById(1L).getStock();
    }

    @Test
    void onlyExpiredUnpaidOrdersRestoreStockAndRepeatedRunsDoNotRestoreTwice() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CouponOrder first = order("expired-1", CouponOrder.STATUS_UNPAID, now.minusMinutes(1));
        CouponOrder second = order("expired-2", CouponOrder.STATUS_UNPAID, now.minusMinutes(2));
        CouponOrder pending = order("pending", CouponOrder.STATUS_UNPAID, now.plusHours(1));
        CouponOrder paid = order("paid", CouponOrder.STATUS_PAID, now.minusMinutes(1));
        CouponOrder closed = order("closed", CouponOrder.STATUS_CLOSED, now.minusMinutes(1));
        CouponOrder refunded = order("refunded", CouponOrder.STATUS_REFUNDED, now.minusMinutes(1));

        task.processTimeOutCouponOrders();

        assertThat(stock()).isEqualTo(98);
        for (CouponOrder expired : List.of(first, second)) {
            var saved = orders.selectById(expired.getId());
            assertThat(saved.getStatus()).isEqualTo(CouponOrder.STATUS_CLOSED);
            assertThat(saved.getCancelTime()).isNotNull();
            assertThat(saved.getCancelReason()).isEqualTo("超时未支付");
        }
        for (CouponOrder unchanged : List.of(pending, paid, closed, refunded)) {
            assertThat(orders.selectById(unchanged.getId()).getStatus()).isEqualTo(unchanged.getStatus());
        }

        task.processTimeOutCouponOrders();
        assertThat(stock()).isEqualTo(98);
    }

    @Test
    void paymentAfterCandidateQueryPreventsClosingAndRestoringStock() {
        CouponOrder candidate = order("paid-after-query", CouponOrder.STATUS_UNPAID, LocalDateTime.now().minusMinutes(1));
        CouponOrderMapper staleQuery = spy(orders);
        doReturn(List.of(candidate)).when(staleQuery).getExpiredUnpaidOrders(any());
        jdbc.update("UPDATE coupon_order SET status = 1, pay_time = ? WHERE id = ?", LocalDateTime.now(), candidate.getId());

        taskWith(staleQuery, coupons).processTimeOutCouponOrders();

        assertThat(orders.selectById(candidate.getId()).getStatus()).isEqualTo(CouponOrder.STATUS_PAID);
        assertThat(stock()).isEqualTo(96);
    }

    @Test
    void stockRestoreFailureRollsBackOrderClosureAndEarlierStockRestores() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(1);
        CouponOrder first = order("rollback-1", CouponOrder.STATUS_UNPAID, deadline);
        CouponOrder second = order("rollback-2", CouponOrder.STATUS_UNPAID, deadline);
        CouponMapper failingStock = spy(coupons);
        doCallRealMethod().doThrow(new IllegalStateException("库存更新失败")).when(failingStock).addStockByOne(1L);

        assertThatThrownBy(() -> taskWith(orders, failingStock).processTimeOutCouponOrders())
                .isInstanceOf(IllegalStateException.class).hasMessage("库存更新失败");

        assertThat(stock()).isEqualTo(96);
        for (CouponOrder order : List.of(first, second)) {
            var saved = orders.selectById(order.getId());
            assertThat(saved.getStatus()).isEqualTo(CouponOrder.STATUS_UNPAID);
            assertThat(saved.getCancelTime()).isNull();
            assertThat(saved.getCancelReason()).isNull();
        }
    }
}
