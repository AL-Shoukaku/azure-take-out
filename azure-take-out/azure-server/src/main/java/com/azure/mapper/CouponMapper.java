package com.azure.mapper;

import com.azure.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CouponMapper {
    /**
     * 向表中插入一条优惠券记录
     * @param coupon    优惠券信息
     */
    @Select("INSERT INTO coupon (name, type, total_stock, stock, discount_amount, threshold_amount, price, status, start_time, end_time, valid_time, create_time, update_time) " +
            "VALUES (#{name}, #{type}, #{totalStock}, #{stock}, #{discountAmount}, #{thresholdAmount}, #{price}, #{status}, #{startTime}, #{endTime}, #{validTime}, #{createTime}, #{updateTime})")
    void insertCoupon(Coupon coupon);
}
