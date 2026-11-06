package com.azure.dto.coupon;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CouponUpdateDTO {

    /** 优惠券ID */
    @Positive(message = "优惠券ID必须大于 0")
    @NotNull(message = "优惠券ID不能 null")
    private Long id;

    /** 优惠券名称 */
    @NotNull(message = "优惠券名称不能 null")
    @NotBlank(message = "优惠券名称不能为空")
    private String name;


    /** 代金券的购买价格 */
    @Min(value = 0, message = "代金券的购买价格不能小于 0")
    private Double price;
}
