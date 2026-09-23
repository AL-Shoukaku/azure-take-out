package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrdersMapper {

    /**
     * 用户下单
     */
    void submitOrder(Orders orders);

    /**
     * 根据订单号查询订单
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 根据id查询订单
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 修改订单信息
     */
    void update(Orders orders);

    /**
     * 分页查询用户的历史订单，支持按状态查询
     */
    Page<Orders> pageQuery(Integer userId, Integer status);

    /**
     * 分页查询商家的订单，支持条件查询
     */
    Page<Orders> adminPageSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 统计指定状态的订单数量
     */
    Integer countByStatus(Integer status);

    /**
     * 获取指定status订单状态以及下单时间在 orderTime 之前的订单
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTime(Integer status, LocalDateTime orderTime);
}
