package com.azure.mapper;

import com.azure.entity.UserCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserCouponMapper {

    @Select("SELECT COUNT(*) FROM user_coupon WHERE user_id = #{userId} AND coupon_id = #{couponId}")
    int userHasGetCoupon(Long userId, Long couponId);

    @Insert("INSERT INTO user_coupon (user_id, coupon_id, number, type, name, discount_amount, threshold_amount, status, expire_time, create_time) " +
            "VALUES (#{userId}, #{couponId}, #{number}, #{type}, #{name}, #{discountAmount}, #{thresholdAmount}, #{status}, #{expireTime}, #{createTime})")
    void insertUserCoupon(UserCoupon userCoupon);

    @Select("SELECT * FROM user_coupon WHERE number = #{couponNumber}")
    UserCoupon getByCouponNumber(String couponNumber);

    /**
     * 更新用户优惠券状态为已退款
     * @param userId            用户id
     * @param couponId          优惠券id
     * @param expectedStatus    预期状态
     * @param newStatus         新状态
     * @param refundTime        退款时间
     * @return                  更新的记录数，若为0则表示更新失败（可能是优惠券状态不匹配）
     */
    @Update("UPDATE user_coupon SET status = #{newStatus}, refund_time = #{refundTime} WHERE user_id = #{userId} AND coupon_id = #{couponId} AND status = #{expectedStatus}")
    int updateToRefund(Long userId, Long couponId, Integer expectedStatus, Integer newStatus, LocalDateTime refundTime);
}
