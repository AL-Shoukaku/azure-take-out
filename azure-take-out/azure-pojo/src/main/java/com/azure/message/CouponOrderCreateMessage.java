package com.azure.message;

import com.azure.entity.Coupon;
import lombok.Data;

@Data
public class CouponOrderCreateMessage {

    /** 优惠券模板信息 */
    private Coupon coupon;

    /** 优惠券号 */
    private String couponNumber;

    /** 订单号 */
    private String orderNumber;

    /** 用户 id */
    private Long userId;
}