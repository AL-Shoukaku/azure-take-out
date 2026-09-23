package com.sky.task;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrdersMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(cron = "0 * * * * *") // 每分钟执行一次
    public void processTimeOutOrders() {
        log.info("处理超时订单:{}", LocalDateTime.now());
        List<Orders> ordersList = ordersMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(15));
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时未支付，系统自动取消");
                orders.setCancelTime(LocalDateTime.now());
                ordersMapper.update(orders);
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
                orders.setStatus(Orders.COMPLETED);
                orders.setDeliveryTime(LocalDateTime.now());
                ordersMapper.update(orders);
            }
        }
    }
}
