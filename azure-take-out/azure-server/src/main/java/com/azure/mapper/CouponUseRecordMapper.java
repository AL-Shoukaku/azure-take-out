package com.azure.mapper;

import com.azure.entity.CouponUseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CouponUseRecordMapper {

    /**
     * 批量插入优惠券使用记录
     * @param couponUseRecordList 优惠券使用记录列表，不能为空列表
     */
    void batchInsert(@Param("couponUseRecordList") List<CouponUseRecord> couponUseRecordList);

    /**
     * 根据订单ID查询该订单使用的优惠券信息
     * @param orderId 订单ID
     * @return 该订单使用的优惠券信息列表
     */
    @Select("select * from coupon_use_record where order_id = #{orderId}")
    List<CouponUseRecord> getByOrderId(@Param("orderId") Long orderId);
}
