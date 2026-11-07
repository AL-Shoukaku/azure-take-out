package com.azure.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户持有的优惠券实体类
 */
@Data
public class UserCoupon {

    public static final Integer STATUS_UNUSED = 0; // 未使用
    public static final Integer STATUS_LOCKED = 1; // 锁定中
    public static final Integer STATUS_USED = 2; // 已使用
    public static final Integer STATUS_EXPIRED = 3; // 已过期
    public static final Integer STATUS_REFUNDED = 4; // 购券款已退款

    /** 用户优惠券ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 优惠券模板ID */
    private Long couponId;

    /** 券号 */
    private String number;

    /** 券类型快照，1代金券 2满减券 */
    private Integer type;

    /** 券名称快照 */
    private String name;

    /** 券面额快照 */
    private BigDecimal discountAmount;

    /** 门槛金额快照，代金券为0 */
    private BigDecimal thresholdAmount;

    /** 状态，0未使用 1锁定中 2已使用 3已过期 4购券款已退款 */
    private Integer status;

    /** 锁定订单ID，锁定中和已使用时保留 */
    private Long lockOrderId;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 领取成功或购券支付成功时间 */
    private LocalDateTime createTime;

    /** 核销时间 */
    private LocalDateTime useTime;

    /** 购券退款时间 */
    private LocalDateTime refundTime;
}
