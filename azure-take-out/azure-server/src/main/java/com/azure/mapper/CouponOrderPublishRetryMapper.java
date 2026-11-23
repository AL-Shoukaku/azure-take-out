package com.azure.mapper;

import com.azure.entity.CouponOrderPublishRetry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CouponOrderPublishRetryMapper {

    /**
     * 插入一条待补发的购券消息，并回填记录ID。
     */
    @Insert("INSERT INTO coupon_order_publish_retry (coupon_id, user_id, order_number, message) " +
            "VALUES (#{couponId}, #{userId}, #{orderNumber}, #{message})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertCouponOrderPublishRetry(CouponOrderPublishRetry record);

    @Select("""
            SELECT id, coupon_id, user_id, order_number, message
            FROM coupon_order_publish_retry
            ORDER BY id
            LIMIT #{limit}
            """)
    List<CouponOrderPublishRetry> selectPending(@Param("limit") int limit);

    @Delete("DELETE FROM coupon_order_publish_retry WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
