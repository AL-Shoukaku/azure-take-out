package com.azure.service.impl;

import com.azure.dto.ShoppingCartDTO;
import com.azure.entity.*;
import com.azure.mapper.*;
import com.azure.support.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShoppingCartServiceImplTest extends ServiceTestSupport {
    @InjectMocks private ShoppingCartServiceImpl service;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private DishMapper dishMapper;
    @Mock private SetmealMapper setmealMapper;
    @Captor private ArgumentCaptor<ShoppingCart> cart;

    private ShoppingCartDTO dishRequest() {
        ShoppingCartDTO dto = new ShoppingCartDTO(); dto.setDishId(7L); dto.setDishFlavor("微辣"); return dto;
    }
    @Test void existingItemIncrementsQuantityAndPreservesUnitPrice() {
        ShoppingCart row = ShoppingCart.builder().id(10L).number(2).amount(new BigDecimal("12.50")).build();
        when(shoppingCartMapper.list(any())).thenReturn(Collections.singletonList(row));
        service.addShoppingCart(dishRequest()); verify(shoppingCartMapper).update(row);
        assertThat(row.getNumber()).isEqualTo(3); assertThat(row.getAmount()).isEqualByComparingTo("12.50");
        verify(shoppingCartMapper, never()).insert(any()); verifyNoInteractions(dishMapper,setmealMapper);
        verify(shoppingCartMapper).list(cart.capture());
        assertThat(cart.getValue()).extracting(ShoppingCart::getUserId,ShoppingCart::getDishId,ShoppingCart::getDishFlavor)
                .containsExactly(USER_ID,7L,"微辣");
    }
    @ParameterizedTest @NullAndEmptySource
    void newDishUsesCatalogPriceAndCurrentUser(List<ShoppingCart> existing) {
        when(shoppingCartMapper.list(any())).thenReturn(existing);
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().name("鱼香肉丝").image("dish.png").price(new BigDecimal("19.90")).build());
        LocalDateTime before = LocalDateTime.now(); service.addShoppingCart(dishRequest());
        verify(shoppingCartMapper).insert(cart.capture());
        assertThat(cart.getValue()).extracting(ShoppingCart::getUserId,ShoppingCart::getDishId,ShoppingCart::getDishFlavor,
                ShoppingCart::getNumber,ShoppingCart::getName,ShoppingCart::getImage)
                .containsExactly(USER_ID,7L,"微辣",1,"鱼香肉丝","dish.png");
        assertThat(cart.getValue().getAmount()).isEqualByComparingTo("19.90");
        assertThat(cart.getValue().getCreateTime()).isBetween(before,LocalDateTime.now());
        verifyNoInteractions(setmealMapper);
    }
    @Test void newSetmealUsesCatalogPrice() {
        ShoppingCartDTO dto = new ShoppingCartDTO(); dto.setSetmealId(8L);
        when(shoppingCartMapper.list(any())).thenReturn(Collections.emptyList());
        when(setmealMapper.getById(8L)).thenReturn(Setmeal.builder().name("午餐套餐").image("setmeal.png").price(new BigDecimal("29.90")).build());
        service.addShoppingCart(dto); verify(shoppingCartMapper).insert(cart.capture());
        assertThat(cart.getValue().getSetmealId()).isEqualTo(8L);
        assertThat(cart.getValue().getName()).isEqualTo("午餐套餐");
        assertThat(cart.getValue().getImage()).isEqualTo("setmeal.png");
        assertThat(cart.getValue().getAmount()).isEqualByComparingTo("29.90"); verifyNoInteractions(dishMapper);
    }
    @Test void listRestrictsToCurrentUser() {
        ShoppingCart row = ShoppingCart.builder().id(10L).build();
        when(shoppingCartMapper.list(any())).thenReturn(Collections.singletonList(row));
        assertThat(service.showShoppingCart()).containsExactly(row);
        verify(shoppingCartMapper).list(cart.capture()); assertThat(cart.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(cart.getValue().getDishId()).isNull(); assertThat(cart.getValue().getSetmealId()).isNull();
    }
    @Test void cleanRestrictsToCurrentUser() { service.cleanShoppingCart(); verify(shoppingCartMapper).cleanAllByUserId(USER_ID); }
    @Test void subtractDecrementsWithoutChangingUnitPrice() {
        ShoppingCart row = ShoppingCart.builder().id(10L).number(3).amount(new BigDecimal("12.50")).build();
        when(shoppingCartMapper.list(any())).thenReturn(Collections.singletonList(row));
        service.subShoppingCart(dishRequest()); verify(shoppingCartMapper).update(row);
        assertThat(row.getNumber()).isEqualTo(2); assertThat(row.getAmount()).isEqualByComparingTo("12.50");
        verify(shoppingCartMapper,never()).deleteById(anyLong());
    }
    @Test void subtractLastItemDeletesRow() {
        when(shoppingCartMapper.list(any())).thenReturn(Collections.singletonList(ShoppingCart.builder().id(10L).number(1).build()));
        service.subShoppingCart(dishRequest()); verify(shoppingCartMapper).deleteById(10L);
        verify(shoppingCartMapper,never()).update(any());
        verify(shoppingCartMapper).list(cart.capture()); assertThat(cart.getValue().getUserId()).isEqualTo(USER_ID);
    }
    @ParameterizedTest @NullAndEmptySource
    void subtractMissingItemDoesNothing(List<ShoppingCart> existing) {
        when(shoppingCartMapper.list(any())).thenReturn(existing);
        service.subShoppingCart(dishRequest());
        verify(shoppingCartMapper,never()).update(any()); verify(shoppingCartMapper,never()).deleteById(anyLong());
    }
}
