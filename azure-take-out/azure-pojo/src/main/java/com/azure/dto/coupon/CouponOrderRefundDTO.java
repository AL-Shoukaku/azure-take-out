package com.azure.dto.coupon;

import lombok.Data;

@Data
public class CouponOrderRefundDTO {
    Long id;

    String refundReason;
}
