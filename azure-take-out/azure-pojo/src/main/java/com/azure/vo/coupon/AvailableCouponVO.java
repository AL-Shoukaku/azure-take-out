package com.azure.vo.coupon;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 封装在售优惠券信息的VO类 */
@Data
public class AvailableCouponVO {
    Long id;

    String name;

    int type;

    int stock;

    BigDecimal discountAmount;

    BigDecimal thresholdAmount;

    BigDecimal price;

    LocalDateTime startTime;

    LocalDateTime endTime;
}
