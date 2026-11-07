package com.azure.vo.coupon;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券订单详情返回对象，包含订单全部字段
 */
@Data
public class CouponOrderVO implements Serializable {

    /** 购券订单ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 优惠券模板ID */
    private Long couponId;

    /** 购券订单号 */
    private String number;

    /** 预生成的券号 */
    private String couponNumber;

    /** 用户名快照 */
    private String username;

    /** 券名称快照 */
    private String couponName;

    /** 券类型，当前仅支持1代金券 */
    private Integer type;

    /** 购买价格快照 */
    private BigDecimal price;

    /** 券面额快照 */
    private BigDecimal discountAmount;

    /** 门槛金额快照，代金券为0 */
    private BigDecimal thresholdAmount;

    /** 状态，0未支付 1已支付 2已退款 3已关闭 */
    private Integer status;

    /** 支付截止时间 */
    private LocalDateTime payDeadline;

    /** 订单创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 关闭时间 */
    private LocalDateTime cancelTime;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 关闭原因 */
    private String cancelReason;

    /** 退款原因 */
    private String refundReason;
}
