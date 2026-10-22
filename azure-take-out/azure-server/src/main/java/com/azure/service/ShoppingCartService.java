package com.azure.service;

import com.azure.dto.ShoppingCartDTO;
import com.azure.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 用户添加购物车
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查询购物车数据
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 清空购物车
     */
    void cleanShoppingCart();

    /**
     * 用户减少购物车的菜品数量
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);
}
