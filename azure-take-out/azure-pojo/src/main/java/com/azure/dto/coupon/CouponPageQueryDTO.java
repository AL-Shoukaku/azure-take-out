package com.azure.dto.coupon;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CouponPageQueryDTO {

    /** 当前页码 */
    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;

    /** 每页显示条数 */
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private int pageSize = 10;

    /** 优惠券名称 */
    private String name;

    /** 优惠券状态，1启用，0禁用 */
    private Integer status;

    /** 优惠券类型，1代金券，2满减券 */
    private Integer type;

    /** 活动状态，0未开始，1进行中，2已结束 */
    private Integer activityStatus;
}
