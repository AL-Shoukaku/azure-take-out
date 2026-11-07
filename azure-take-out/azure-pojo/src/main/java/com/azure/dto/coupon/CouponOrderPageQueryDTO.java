package com.azure.dto.coupon;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CouponOrderPageQueryDTO implements Serializable {

    /** 当前页码 */
    private int page = 1;

    /** 每页显示条数 */
    private int pageSize = 10;

    /** 购券订单号，模糊匹配 */
    private String number;

    /** 下单用户名，模糊匹配 */
    private String username;

    /** 订单状态，0未支付 1已支付 2已退款 3已关闭 */
    private Integer status;

    /** 下单时间范围-开始 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    /** 下单时间范围-结束 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

}
