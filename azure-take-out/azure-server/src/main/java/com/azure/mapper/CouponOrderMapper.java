package com.azure.mapper;

import com.azure.entity.CouponOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CouponOrderMapper {

    /**
     * 插入一条优惠券购买订单，并回填订单ID
     * @param couponOrder 购券订单信息
     */
    @Insert("INSERT INTO coupon_order (user_id, coupon_id, number, coupon_number, username, coupon_name, " +
            "type, price, discount_amount, threshold_amount, status, pay_deadline, create_time, " +
            "pay_time, cancel_time, refund_time, cancel_reason, refund_reason) " +
            "VALUES (#{userId}, #{couponId}, #{number}, #{couponNumber}, #{username}, #{couponName}, " +
            "#{type}, #{price}, #{discountAmount}, #{thresholdAmount}, #{status}, #{payDeadline}, #{createTime}, " +
            "#{payTime}, #{cancelTime}, #{refundTime}, #{cancelReason}, #{refundReason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertCouponOrder(CouponOrder couponOrder);

    @Select("SELECT COUNT(*) FROM coupon_order WHERE user_id = #{userId} AND coupon_id = #{couponId}")
    int userHasBuyCoupon(Long userId, Long couponId);
}
