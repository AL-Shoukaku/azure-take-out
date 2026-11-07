package com.azure.vo.coupon;

import lombok.Data;

import java.time.LocalDateTime;

/** 封装在售优惠券信息的VO类 */
@Data
public class AvailableCouponVO {
    Long id;

    String name;

    int type;

    int stock;

    double discountAmount;

    double thresholdAmount;

    double price;

    LocalDateTime startTime;

    LocalDateTime endTime;
}
