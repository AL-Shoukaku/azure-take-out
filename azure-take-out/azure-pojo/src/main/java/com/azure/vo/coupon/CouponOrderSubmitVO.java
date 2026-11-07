package com.azure.vo.coupon;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券购买下单结果
 */
@Data
@Builder
public class CouponOrderSubmitVO {

    /** 购券订单号 */
    private String number;

    /** 应付金额 */
    private BigDecimal price;

    /** 支付截止时间 */
    private LocalDateTime payDeadline;
}
