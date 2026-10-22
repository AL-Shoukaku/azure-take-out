package com.azure.service.impl;

import com.azure.context.BaseContext;
import com.azure.dto.ShoppingCartDTO;
import com.azure.entity.Dish;
import com.azure.entity.Setmeal;
import com.azure.entity.ShoppingCart;
import com.azure.mapper.DishMapper;
import com.azure.mapper.SetmealMapper;
import com.azure.mapper.ShoppingCartMapper;
import com.azure.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 用户添加购物车
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 先判断商品是否已经在购物车里了，
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);

        // 如果在则数量加 1,如果不在，执行插入操作
        if (shoppingCarts != null && !shoppingCarts.isEmpty()) {
            // 如果查出来了，只可能有一个
            // amount 是单价，下单时由服务端按单价和数量计算总金额
            shoppingCart = shoppingCarts.get(0);
            shoppingCart.setNumber(shoppingCart.getNumber() + 1);
            shoppingCartMapper.update(shoppingCart);
        } else {
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            if (shoppingCart.getDishId() != null) {
                // 是菜品
                Dish dish = dishMapper.getById(shoppingCart.getDishId());
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            } else {
                // 是套餐
                Setmeal setmeal = setmealMapper.getById(shoppingCart.getSetmealId());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 获取当前用户的购物车数据
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        return shoppingCartMapper.list(shoppingCart);
    }

    /**
     * 清空当前用户的购物车
     */
    @Override
    public void cleanShoppingCart() {
        shoppingCartMapper.cleanAllByUserId(BaseContext.getCurrentId());
    }

    /**
     * 用户减少购物车中的菜品数量
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);

        if (shoppingCarts != null && !shoppingCarts.isEmpty()) {
            // 如果查出来了，只可能有一个
            // 如果数量为大于 1，则使用 update 减 1，如果数量为 1 则直接删除
            shoppingCart = shoppingCarts.get(0);
            if (shoppingCart.getNumber() > 1) {
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.update(shoppingCart);
            } else {
                // 数量为 1，直接删除
                shoppingCartMapper.deleteById(shoppingCart.getId());
            }
        }
    }
}
