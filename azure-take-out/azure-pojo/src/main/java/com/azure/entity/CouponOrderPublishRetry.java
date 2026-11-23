package com.azure.entity;

import lombok.Data;

/**
 * 待补发的购券消息
 */
@Data
public class CouponOrderPublishRetry {

    /** 补发记录ID */
    private Long id;

    /** 优惠券ID */
    private Long couponId;

    /** 用户ID */
    private Long userId;

    /** 购券订单号 */
    private String orderNumber;

    /** 原发送消息的JSON字符串 */
    private String message;
}
