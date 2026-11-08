package com.azure.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外卖订单优惠券使用记录
 */
@Data
public class CouponUseRecord {

    /** 使用记录ID */
    private Long id;

    /** 外卖订单ID */
    private Long orderId;

    /** 用户ID */
    private Long userId;

    /** 优惠券模板ID */
    private Long couponId;

    /** 用户优惠券ID */
    private Long userCouponId;

    /** 券号快照 */
    private String number;

    /** 券类型快照，1代金券 2满减券 */
    private Integer type;

    /** 券名称快照 */
    private String name;

    /** 券面额快照 */
    private BigDecimal discountAmount;

    /** 门槛金额快照 */
    private BigDecimal thresholdAmount;

    /** 本次订单锁券时间 */
    private LocalDateTime createTime;
}
