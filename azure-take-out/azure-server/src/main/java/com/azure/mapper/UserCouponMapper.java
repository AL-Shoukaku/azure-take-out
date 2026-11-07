package com.azure.mapper;

import com.azure.entity.UserCoupon;
import com.azure.vo.coupon.UserCouponVO;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserCouponMapper {

    @Select("SELECT COUNT(*) FROM user_coupon WHERE user_id = #{userId} AND coupon_id = #{couponId}")
    int userHasGetCoupon(Long userId, Long couponId);

    @Insert("INSERT INTO user_coupon (user_id, coupon_id, number, type, name, discount_amount, threshold_amount, status, lock_order_id, expire_time, create_time, use_time, refund_time) " +
            "VALUES (#{userId}, #{couponId}, #{number}, #{type}, #{name}, #{discountAmount}, #{thresholdAmount}, #{status}, #{lockOrderId}, #{expireTime}, #{createTime}, #{useTime}, #{refundTime})")
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

    /**
     * 分页查询用户持有的优惠券，支持按状态筛选
     */
    Page<UserCouponVO> userPageQuery(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 根据优惠券ID和用户ID查询本人持有的优惠券详情
     */
    UserCouponVO selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 将已经过期的用户优惠券状态更新为已过期
     * 只有当优惠券当前状态为未使用时才会更新成功，锁定中，已使用，已退款的优惠券不受影响
     */
    @Update("UPDATE user_coupon SET status = 3 WHERE status = 0 AND expire_time < NOW()")
    void updateExpiredUserCouponsToExpired();
}
