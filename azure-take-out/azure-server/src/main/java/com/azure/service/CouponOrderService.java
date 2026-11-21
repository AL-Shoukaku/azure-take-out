package com.azure.service;

import com.azure.dto.coupon.CouponOrderPageQueryDTO;
import com.azure.dto.coupon.CouponOrderRefundDTO;
import com.azure.result.PageResult;
import com.azure.vo.coupon.CouponOrderSubmitVO;
import com.azure.vo.coupon.CouponOrderVO;

public interface CouponOrderService {

    /**
     * 商家分页查询优惠券订单，支持按订单号、用户名、下单时间、订单状态筛选
     */
    PageResult adminPageQuery(CouponOrderPageQueryDTO couponOrderPageQueryDTO);

    /**
     * 商家查询优惠券订单详情
     */
    CouponOrderVO queryOrderDetail(Long id);

    /**
     * 用户分页查询自己的优惠券订单，支持按订单状态筛选
     */
    PageResult historyOrdersPageQuery(Integer page, Integer pageSize, Integer status);

    /**
     * 用户查询本人的优惠券订单详情
     */
    CouponOrderVO queryUserOrderDetail(Long id);

    /**
     * 商家退款优惠券订单
     */
    void refundCouponOrder(CouponOrderRefundDTO couponOrderRefundDTO);

    /**
     * 用户查询是否拥有某个优惠券订单
     * 用于异步下单时，前端轮询查询是否已经生成订单
     */
    CouponOrderSubmitVO hasCouponOrder(String couponNumber);

    /**
     * 按订单号查询本人的购券订单，查不到时返回 null。
     */
    CouponOrderSubmitVO queryUserOrderStatus(String orderNumber);
}
