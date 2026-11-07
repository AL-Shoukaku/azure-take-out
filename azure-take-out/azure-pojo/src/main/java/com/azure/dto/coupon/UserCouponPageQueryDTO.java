package com.azure.dto.coupon;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserCouponPageQueryDTO implements Serializable {

    /** 当前页码 */
    private int page = 1;

    /** 每页显示条数 */
    private int pageSize = 10;

    /** 优惠券状态，0未使用 1锁定中 2已使用 3已过期 4购券款已退款，不传查询全部 */
    private Integer status;
}
