package com.sky.mapper;

import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 查询满足条件的购物车数据
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 新增购物车数据
     */
    void update(ShoppingCart shoppingCart);

    /**
     * 向数据库中插入一项
     */
    @Insert("insert into shopping_cart (name, user_id, dish_id, setmeal_id, dish_flavor, number, amount, image, create_time) " +
            "values (#{name}, #{userId}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{amount}, #{image}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 根据用户id清空购物车
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void cleanAllByUserId(Long userId);

    /**
     * 根据 id 删除购物车数据
     */
    @Delete("delete from shopping_cart where id = #{id}")
    void deleteById(Long id);

    /**
     * 批量插入购物车数据
     * @param shoppingCartList
     */
    void batchInsert(List<ShoppingCart> shoppingCartList);

    /**
     *  根据菜品 id 删除购物车数据
     * @param dishId
     */
    @Delete("delete from shopping_cart where dish_id = #{dishId}")
    void deleteByDishId(Long dishId);

    /**
     * 根据菜品更新购物车数据
     * @param dish
     */
    @Update("update shopping_cart set name = #{name}, amount = #{price}, image = #{image} where dish_id = #{id}")
    void updateByDish(Dish dish);

    /**
     * 根据套餐 id 删除购物车数据
     * @param setmealId
     */
    @Delete("delete from shopping_cart where setmeal_id = #{setmealId}")
    void deleteBySetmealId(Long setmealId);

    /**
     * 根据套餐更新购物车数据
     * @param setmeal
     */
    @Update("update shopping_cart set name = #{name}, amount = #{price}, image = #{image} where setmeal_id = #{id}")
    void updateBySetmeal(Setmeal setmeal);
}
