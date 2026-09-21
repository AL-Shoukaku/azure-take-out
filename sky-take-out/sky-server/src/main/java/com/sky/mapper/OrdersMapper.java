package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrdersMapper {

    /**
     * 用户下单
     */
    void submitOrder(Orders orders);
}
