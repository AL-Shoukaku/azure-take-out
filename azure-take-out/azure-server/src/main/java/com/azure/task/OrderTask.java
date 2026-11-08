package com.azure.task;

import com.azure.entity.Orders;
import com.azure.mapper.OrdersMapper;
import com.azure.mapper.UserCouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自定义定时任务类，定时处理订单状态
 */
@Slf4j
@Component
public class OrderTask {

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Scheduled(cron = "0 * * * * *") // 每分钟执行一次
    @Transactional
    public void processTimeOutOrders() {
        log.info("处理超时订单:{}", LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        List<Orders> ordersList = ordersMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(15));
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders orders : ordersList) {
                Orders cancelled = Orders.builder()
                        .id(orders.getId())
                        .status(Orders.CANCELLED)
                        .cancelReason("订单超时未支付，系统自动取消")
                        .cancelTime(LocalDateTime.now())
                        .build();
                if (ordersMapper.update(cancelled, Orders.PENDING_PAYMENT) == 0) {
                    log.info("处理超时订单时，订单状态已变化，跳过订单，订单ID: {}", orders.getId());
                    continue;
                }
                // 退回优惠券给用户
                int rows = userCouponMapper.UpdateStatusWhenOrderRefund(orders.getId());
                if (rows != orders.getCouponCount()) {
                    log.warn("处理超时订单时，退回优惠券数量不匹配，订单ID: {}, 预期退回数量: {}, 实际退回数量: {}", orders.getId(), orders.getCouponCount(), rows);
                    throw new RuntimeException("处理超时订单时，更新优惠券状态发生异常");
                }
            }
        }
    }

    /**
     * 每天凌晨 1 点处理一直处于配送中的订单，防止订单长时间处于配送中状态
     */
    @Scheduled(cron = "0 0 1 * * *") // 每天凌晨 1 点执行一次
    public void processDeliveryOrders() {
        log.info("凌晨 1 点处理配送中的订单:{}", LocalDateTime.now());
        // 减去一个小时，获取上一个工作日中派送中的订单
        List<Orders> ordersList = ordersMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, LocalDateTime.now().minusHours(1));
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders orders : ordersList) {
                Orders completed = Orders.builder()
                        .id(orders.getId())
                        .status(Orders.COMPLETED)
                        .deliveryTime(LocalDateTime.now())
                        .build();
                ordersMapper.update(completed, Orders.DELIVERY_IN_PROGRESS);
            }
        }
    }
}
