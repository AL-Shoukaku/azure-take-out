package com.azure.mapper;

import com.azure.dto.coupon.CouponPageQueryDTO;
import com.azure.entity.Coupon;
import com.azure.result.PageResult;
import com.azure.vo.coupon.CouponVO;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponMapper {
    /**
     * 向表中插入一条优惠券记录
     * @param coupon    优惠券信息
     */
    @Insert("INSERT INTO coupon (name, type, total_stock, stock, discount_amount, threshold_amount, price, status, start_time, end_time, valid_time, create_time, update_time) " +
            "VALUES (#{name}, #{type}, #{totalStock}, #{stock}, #{discountAmount}, #{thresholdAmount}, #{price}, #{status}, #{startTime}, #{endTime}, #{validTime}, #{createTime}, #{updateTime})")
    void insertCoupon(Coupon coupon);

    /**
     * 更新优惠券信息
     * @param coupon    优惠券信息
     */
    int updateCoupon(Coupon coupon);

    /**
     * 根据优惠券ID查询优惠券详情
     * @param couponId 优惠券ID
     * @return 优惠券详情
     */
    @Select("SELECT * FROM coupon WHERE id = #{couponId}")
    Coupon selectById(Long couponId);

    /**
     * 分页查询优惠券列表
     * @param couponPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    Page<CouponVO> pageQuery(CouponPageQueryDTO couponPageQueryDTO);

    /**
     * 更新优惠券状态
     * @param couponId 优惠券ID
     * @param status   新的状态值
     */
    @Update("UPDATE coupon SET status = #{status}, update_time = NOW() WHERE id = #{couponId}")
    int updateStatus(Long couponId, Integer status);
}
