package com.sky.service;

import com.sky.dto.ShoppingCartDTO;

public interface ShoppingCartService {

    /**
     * 用户添加购物车
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);
}
