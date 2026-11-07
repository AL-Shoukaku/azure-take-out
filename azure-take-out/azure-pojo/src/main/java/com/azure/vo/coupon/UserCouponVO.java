package com.azure.vo.coupon;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户持有优惠券的返回对象，不含 userId、lockOrderId 等内部字段
 */
@Data
public class UserCouponVO implements Serializable {

    /** 用户优惠券ID */
    private Long id;

    /** 优惠券模板ID */
    private Long couponId;

    /** 券号 */
    private String number;

    /** 券类型，1代金券 2满减券 */
    private Integer type;

    /** 券名称快照 */
    private String name;

    /** 券面额快照 */
    private BigDecimal discountAmount;

    /** 门槛金额快照，代金券为0 */
    private BigDecimal thresholdAmount;

    /** 状态，0未使用 1锁定中 2已使用 3已过期 4购券款已退款 */
    private Integer status;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 领取成功或购券支付成功时间 */
    private LocalDateTime createTime;

    /** 核销时间 */
    private LocalDateTime useTime;

    /** 购券退款时间 */
    private LocalDateTime refundTime;
}
