package com.sky.service;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrdersService {

    /**
     * 用户下单
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 用户查询历史订单，可支持根据订单状态查询
     */
    PageResult historyOrdersPageQuery(Integer page, Integer pageSize, Integer status);

    /**
     * 用户查询订单详情
     */
    OrderVO queryOrderDetail(Long id);

    /**
     * 用户取消订单
     */
    void userCancelOrder(Long id);

    /**
     * 商家取消订单，需要传入取消原因
     */
    void cancelOrder(OrdersCancelDTO ordersCancelDTO);

    /**
     * 用户再来一单
     */
    void repetition(Long id);

    /**
     * 商家查询订单列表，支持条件查询
     */
    PageResult adminPageQuery(OrdersPageQueryDTO ordersPageQueryDTO);
}
