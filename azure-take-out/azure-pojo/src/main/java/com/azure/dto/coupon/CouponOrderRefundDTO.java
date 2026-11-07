package com.azure.dto.coupon;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CouponOrderRefundDTO {

    @Positive(message = "订单ID必须为正数")
    @NotNull(message     = "订单ID不能为空")
    private Long id;

    @NotBlank(message = "退款原因不能为空")
    private String refundReason;
}
