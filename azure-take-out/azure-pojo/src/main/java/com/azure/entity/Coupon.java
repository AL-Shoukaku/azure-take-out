package com.azure.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Coupon {
    /** 优惠券ID */
    private Long id;
    /** 优惠券名称 */
    private String name;
    /** 优惠券类型，1代金券，2满减券 */
    private Integer type;
    /** 优惠券总库存 */
    private Integer totalStock;
    /** 优惠券剩余库存 */
    private Integer stock;
    /** 抵扣金额 */
    private Double discountAmount;
    /** 满减券的门槛金额 */
    private Double thresholdAmount;
    /** 代金券的购买价格 */
    private Double price;
    /** 优惠券的状态 0停售 1启售 */
    private Integer status;
    /** 优惠券活动开始时间 */
    private LocalDateTime startTime;
    /** 优惠券活动结束时间 */
    private LocalDateTime endTime;
    /** 优惠券的有效期，单位为天 */
    private Integer validTime;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
