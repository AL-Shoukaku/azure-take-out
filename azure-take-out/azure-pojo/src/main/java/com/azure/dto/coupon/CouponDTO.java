package com.azure.dto.coupon;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券DTO，用于发布优惠券时传递信息
 */
@Data
public class CouponDTO {

    @NotNull(message = "优惠券名称不能 null")
    @NotBlank(message = "优惠券名称不能为空")
    /** 优惠券名称 */
    private String name;

    @Max(value = 2, message = "优惠券类型只能是 1 或 2")
    @Min(value = 1, message = "优惠券类型只能是 1 或 2")
    /** 优惠券类型，1代金券，2满减券 */
    private int type;

    @Positive(message = "优惠券总库存必须大于 0")
    /** 优惠券总库存 */
    private int totalStock;

    @Positive(message = "抵扣金额必须大于 0")
    /** 抵扣金额 */
    private double discountAmount;

    @Positive(message = "满减券的门槛金额必须大于 0")
    /** 满减券的门槛金额 */
    private double thresholdAmount;

    @Positive(message = "代金券的购买价格必须大于 0")
    /** 代金券的购买价格 */
    private double price;

    /** 优惠券活动开始时间 */
    private LocalDateTime startTime;

    /** 优惠券活动结束时间 */
    private LocalDateTime endTime;

    @Positive(message = "优惠券的有效期必须大于 0")
    /** 优惠券的有效期，单位为天 */
    private int validTime;

}
