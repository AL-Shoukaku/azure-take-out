package com.azure.service;

import com.azure.dto.coupon.CouponDTO;
import com.azure.dto.coupon.CouponPageQueryDTO;
import com.azure.dto.coupon.CouponUpdateDTO;
import com.azure.result.PageResult;
import com.azure.vo.coupon.*;

import java.util.List;

public interface CouponService {
    /**
     * 商家发布优惠券
     * @param couponDTO 优惠券信息
     */
    void publishCoupon(CouponDTO couponDTO);

    /**
     * 商家修改优惠券信息
     * @param couponUpdateDTO 优惠券信息
     */
    void updateCoupon(CouponUpdateDTO couponUpdateDTO);

    /**
     * 商家起售/停售优惠券
     * @param couponId 优惠券ID
     * @param status   优惠券状态，1启用，2禁用
     */
    void startOrStop(Long couponId, Integer status);

    /**
     * 根据优惠券ID查询优惠券详情
     * @param couponId 优惠券ID
     * @return 优惠券详情
     */
    CouponVO getById(Long couponId);

    /**
     * 分页查询优惠券列表
     * @param couponPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    PageResult pageQuery(CouponPageQueryDTO couponPageQueryDTO);

    /**
     * 用户查询可领取的优惠券列表
     * 包括未开始的优惠券和正在进行中的优惠券，且库存大于0
     * @return  可领取/购买的优惠券列表
     */
    List<AvailableCouponVO> queryAvailableCoupons();

    /**
     * 用户领取满减券
     * @param couponId  优惠券ID
     */
    CouponClaimVO userGetCoupon(Long couponId);

    /**
     * 用户购买代金券
     * @param couponId  优惠券ID
     */
    CouponOrderSubmitVO userBuyCoupon(Long couponId);

    CouponOrderPayVO payCouponOrder(String couponOrderNumber);
}
