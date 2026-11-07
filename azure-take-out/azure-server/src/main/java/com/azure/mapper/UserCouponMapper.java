package com.azure.mapper;

import com.azure.entity.UserCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserCouponMapper {

    @Select("SELECT COUNT(*) FROM user_coupon WHERE user_id = #{userId} AND coupon_id = #{couponId}")
    int userHasGetCoupon(Long userId, Long couponId);

    @Insert("INSERT INTO user_coupon (user_id, coupon_id, number, type, name, discount_amount, threshold_amount, status, expire_time, create_time) " +
            "VALUES (#{userId}, #{couponId}, #{number}, #{type}, #{name}, #{discountAmount}, #{thresholdAmount}, #{status}, #{expireTime}, #{createTime})")
    void insertUserCoupon(UserCoupon userCoupon);
}
