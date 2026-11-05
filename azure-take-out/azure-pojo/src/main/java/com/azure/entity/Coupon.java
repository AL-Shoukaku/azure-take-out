package com.azure.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Coupon {
    /** 优惠券名称 */
    private String name;
    /** 优惠券类型，1代金券，2满减券 */
    private int type;
    /** 优惠券总库存 */
    private int totalStock;
    /** 优惠券剩余库存 */
    private int stock;
    /** 抵扣金额 */
    private double discountAmount;
    /** 满减券的门槛金额 */
    private double thresholdAmount;
    /** 代金券的购买价格 */
    private double price;
    /** 优惠券的状态 0停售 1启售 */
    private int status;
    /** 优惠券活动开始时间 */
    private LocalDateTime startTime;
    /** 优惠券活动结束时间 */
    private LocalDateTime endTime;
    /** 优惠券的有效期，单位为天 */
    private int validTime;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
