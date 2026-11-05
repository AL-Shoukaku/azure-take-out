package com.azure.service;

import com.azure.dto.coupon.CouponDTO;

public interface CouponService {
    /**
     * 商家发布优惠券
     * @param couponDTO 优惠券信息
     */
    void publishCoupon(CouponDTO couponDTO);
}
