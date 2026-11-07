package com.azure.vo.coupon;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券订单分页查询返回对象，
 * 相比 {@link CouponOrderVO} 不含 userId、couponId、couponNumber、type、createTime 列表不需要的字段
 */
@Data
public class CouponOrderPageQueryVO implements Serializable {

    /** 购券订单ID */
    private Long id;

    /** 购券订单号 */
    private String number;

    /** 用户名快照 */
    private String username;

    /** 券名称快照 */
    private String couponName;

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
