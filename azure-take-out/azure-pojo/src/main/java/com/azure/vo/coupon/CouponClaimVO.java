package com.azure.vo.coupon;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券领取结果
 */
@Data
@Builder
public class CouponClaimVO {

    /** 券号 */
    private String number;

    /** 过期时间 */
    private LocalDateTime expireTime;
}
