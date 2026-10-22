package com.azure.service;

import com.azure.dto.*;
import com.azure.result.PageResult;
import com.azure.vo.OrderPaymentVO;
import com.azure.vo.OrderStatisticsVO;
import com.azure.vo.OrderSubmitVO;
import com.azure.vo.OrderVO;

import java.util.List;

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
     * 用户查询历史订单，可支持根据订单状态查询
     */
    PageResult historyOrdersPageQuery(Integer page, Integer pageSize, Integer status);

    /**
     * 用户查询历史订单，可支持根据订单状态列表查询
     * @param pageNum       页号
     * @param statusList    订单状态列表
     * @return
     */
    PageResult historyOrdersPageQueryByStatusList(Integer pageNum, List<Integer> statusList);

    /**
     * 用户查询本人订单详情
     */
    OrderVO queryUserOrderDetail(Long id);

    /**
     * 管理端查询订单详情，不按当前雇员身份限制用户订单
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

    /**
     * 商家统计各个类别的订单
     */
    OrderStatisticsVO statistics();

    /**
     * 商家完成订单
     */
    void completeOrder(Long id);

    /**
     * 商家接单
     */
    void confirmOrder(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 商家拒单，需要传入拒单原因
     */
    void rejectOrder(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * 商家派送订单
     */
    void deliveryOrder(Long id);

    /**
     * 客户催单
     */
    void remindOrder(Long id);
}
