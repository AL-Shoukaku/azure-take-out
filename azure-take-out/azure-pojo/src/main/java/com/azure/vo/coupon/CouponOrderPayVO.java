package com.azure.vo.coupon;

import lombok.Builder;
import lombok.Data;

/**
 * 优惠券订单支付结果
 */
@Data
@Builder
public class CouponOrderPayVO {

    /** 购券订单号 */
    private String number;
}
