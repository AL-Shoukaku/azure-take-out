package com.sky.mapper;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.service.impl.ShoppingCartServiceImpl;
import com.sky.support.SqlTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingCartFlavorMapperTest extends SqlTestSupport {
    private ShoppingCartMapper cart;
    private ShoppingCartServiceImpl service;

    @BeforeEach
    void setUpCart() {
        cart = mapper(ShoppingCartMapper.class);
        cart.cleanAllByUserId(USER_ID);
        service = new ShoppingCartServiceImpl();
        ReflectionTestUtils.setField(service, "shoppingCartMapper", cart);
        ReflectionTestUtils.setField(service, "dishMapper", mapper(DishMapper.class));
        ReflectionTestUtils.setField(service, "setmealMapper", mapper(SetmealMapper.class));
    }

    @Test
    void nullFlavorMatchesOnlyUnselectedFlavorWhileCartListReturnsBoth() {
        // 测试数据中的菜品 1 有“微辣／中辣”口味；用户也可以不选择口味。
        service.addShoppingCart(dish("微辣"));
        service.addShoppingCart(dish(null));

        assertThat(cart.list(ShoppingCart.builder().userId(USER_ID).dishId(1L).build()))
                .singleElement().satisfies(item -> {
                    assertThat(item.getDishFlavor()).isNull();
                    assertThat(item.getNumber()).isEqualTo(1);
                });
        assertThat(cart.list(ShoppingCart.builder().userId(USER_ID).dishId(1L).dishFlavor("微辣").build()))
                .singleElement().extracting(ShoppingCart::getNumber).isEqualTo(1);
        assertThat(service.showShoppingCart()).hasSize(2);
    }

    @Test
    void addingAndSubtractingUnselectedFlavorDoesNotChangeSelectedFlavor() {
        service.addShoppingCart(dish("微辣"));
        service.addShoppingCart(dish(null));
        service.addShoppingCart(dish(null));

        assertQuantity(null, 2);
        assertQuantity("微辣", 1);

        service.subShoppingCart(dish(null));
        assertQuantity(null, 1);
        assertQuantity("微辣", 1);

        service.subShoppingCart(dish(null));
        assertThat(cart.list(ShoppingCart.builder().userId(USER_ID).dishId(1L).build())).isEmpty();
        assertQuantity("微辣", 1);
        assertThat(service.showShoppingCart()).hasSize(1);
    }

    private void assertQuantity(String flavor, int quantity) {
        assertThat(cart.list(ShoppingCart.builder().userId(USER_ID).dishId(1L).dishFlavor(flavor).build()))
                .singleElement().extracting(ShoppingCart::getNumber).isEqualTo(quantity);
    }

    private ShoppingCartDTO dish(String flavor) {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(1L);
        dto.setDishFlavor(flavor);
        return dto;
    }
}
