package com.azure.mapper;

import com.azure.entity.CouponUseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CouponUseRecordMapper {

    /**
     * 批量插入优惠券使用记录
     * @param couponUseRecordList 优惠券使用记录列表，不能为空列表
     */
    void batchInsert(@Param("couponUseRecordList") List<CouponUseRecord> couponUseRecordList);
}
