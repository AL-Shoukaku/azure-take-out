package com.azure.mapper;

import com.azure.dto.coupon.CouponOrderPageQueryDTO;
import com.azure.entity.CouponOrder;
import com.azure.vo.coupon.CouponOrderPageQueryVO;
import com.azure.vo.coupon.CouponOrderVO;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    @Select("SELECT * FROM coupon_order WHERE number = #{couponOrderNumber} AND user_id = #{userId}")
    CouponOrder selectByNumberAndUserId(String couponOrderNumber, Long userId);

    /**
     * 根据订单ID查询优惠券订单详情
     */
    @Select("SELECT * FROM coupon_order WHERE id = #{id}")
    CouponOrderVO selectById(Long id);

    /**
     * 根据订单ID和用户ID查询本人的优惠券订单详情
     */
    @Select("SELECT * FROM coupon_order WHERE id = #{id} AND user_id = #{userId}")
    CouponOrderVO selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 根据订单ID和预期状态更新订单状态
     * 仅当订单当前状态与预期状态一致时才会更新成功
     * @param id                订单ID
     * @param expectedStatus    预期状态
     * @param newStatus         新状态
     * @param payTime           支付时间
     * @return                  更新的记录数，若为0则表示更新失败（可能是订单状态不匹配）
     */
    @Update("update coupon_order set status = #{newStatus}, pay_time = #{payTime} where id = #{id} and status = #{expectedStatus}")
    int updateStatusByIdWithExpectedStatus(Long id, Integer expectedStatus, Integer newStatus, LocalDateTime payTime);

    /**
     * 查询超时未支付的购券订单
     */
    @Select("select * from coupon_order where status = 0 and pay_deadline <= #{now} order by id")
    List<CouponOrder> getExpiredUnpaidOrders(LocalDateTime now);

    /**
     * 关闭指定的超时未支付订单，成功时返回1
     */
    @Update("update coupon_order set status = 3, cancel_time = #{now}, cancel_reason = '超时未支付' " +
            "where id = #{id} and status = 0 and pay_deadline <= #{now}")
    int closeExpiredUnpaidOrder(Long id, LocalDateTime now);

    /**
     * 分页查询商家的优惠券订单，支持按订单号、用户名、下单时间、订单状态筛选
     */
    Page<CouponOrderPageQueryVO> adminPageSearch(CouponOrderPageQueryDTO couponOrderPageQueryDTO);

    /**
     * 分页查询用户自己的优惠券订单，支持按订单状态筛选
     */
    Page<CouponOrderPageQueryVO> userPageQuery(@Param("userId") Long userId, @Param("status") Integer status);

    @Update("update coupon_order set status = #{newStatus}, refund_reason = #{refundReason}, refund_time = #{refundTime} " +
            "where id = #{id} and status = #{expectedStatus}")
    int updateToRefunded(Long id, String refundReason, Integer expectedStatus, Integer newStatus, LocalDateTime refundTime);
}
