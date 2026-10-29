package com.azure.service.impl;

import com.azure.dto.*;
import com.azure.entity.*;
import com.azure.mapper.*;
import com.azure.support.TransactionTestSupport;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogTransactionIntegrationTest extends TransactionTestSupport {
    @Test void dishUpdateFailureMustRollBackDishAndOriginalFlavors() {
        DishFlavorMapper flavors=spy(transactionalMapper(DishFlavorMapper.class)); doThrow(new IllegalStateException("flavor failure")).when(flavors).insertBatch(any());
        DishServiceImpl target=new DishServiceImpl(); inject(target,"dishMapper",transactionalMapper(DishMapper.class)); inject(target,"dishFlavorMapper",flavors); inject(target,"cacheClient",mockedCacheClient());
        DishDTO dto=new DishDTO(); dto.setId(1L); dto.setName("不应保存的改名"); dto.setFlavors(Collections.singletonList(DishFlavor.builder().name("新口味").value("[]").build()));
        assertThatThrownBy(() -> transactionalService(target,DishServiceImpl.class).updateWithFlavor(dto)).hasMessage("flavor failure");
        assertThat(mapper(DishMapper.class).getById(1L).getName()).isEqualTo("鸡丁");
        assertThat(mapper(DishFlavorMapper.class).getByDishId(1L)).extracting(DishFlavor::getName).containsExactly("辣度");
    }
    @Test void dishDeleteFailureMustRollBackDeletedDish() {
        DishFlavorMapper flavors=spy(transactionalMapper(DishFlavorMapper.class)); doThrow(new IllegalStateException("flavor failure")).when(flavors).batchDeleteByDishIds(any());
        SetmealDishMapper associations=mock(SetmealDishMapper.class); when(associations.getSetMealIdsByDishIds(any())).thenReturn(Collections.emptyList());
        DishServiceImpl target=new DishServiceImpl(); inject(target,"dishMapper",transactionalMapper(DishMapper.class)); inject(target,"dishFlavorMapper",flavors);
        inject(target,"setMealDishMapper",associations); inject(target,"cacheClient",mockedCacheClient());
        assertThatThrownBy(() -> transactionalService(target,DishServiceImpl.class).batchDelete(Collections.singletonList(3L))).hasMessage("flavor failure");
        assertThat(mapper(DishMapper.class).getById(3L)).isNotNull();
    }
    private SetmealServiceImpl setmeals(SetmealDishMapper relations) {
        SetmealServiceImpl target=new SetmealServiceImpl(); inject(target,"setmealMapper",transactionalMapper(SetmealMapper.class)); inject(target,"setmealDishMapper",relations);
        return transactionalService(target,SetmealServiceImpl.class);
    }
    @Test void setmealSaveFailureMustRollBackParentRow() {
        SetmealDishMapper relations=spy(transactionalMapper(SetmealDishMapper.class)); doThrow(new IllegalStateException("relation failure")).when(relations).batchInsert(any());
        SetmealDTO dto=new SetmealDTO(); dto.setName("不应保存的套餐"); dto.setCategoryId(3L); dto.setPrice(BigDecimal.TEN);
        dto.setSetmealDishes(Collections.singletonList(SetmealDish.builder().dishId(1L).copies(1).build()));
        assertThatThrownBy(() -> setmeals(relations).saveWithDish(dto)).hasMessage("relation failure");
        assertThat(mapper(SetmealMapper.class).countByCategoryId(3L)).isEqualTo(2);
    }
    @Test void setmealUpdateFailureMustRollBackParentAndOriginalRelations() {
        SetmealDishMapper relations=spy(transactionalMapper(SetmealDishMapper.class)); doThrow(new IllegalStateException("relation failure")).when(relations).batchInsert(any());
        SetmealDTO dto=new SetmealDTO(); dto.setId(1L); dto.setName("不应保存的改名"); dto.setSetmealDishes(Collections.singletonList(SetmealDish.builder().dishId(3L).copies(1).build()));
        assertThatThrownBy(() -> setmeals(relations).updateWithDishes(dto)).hasMessage("relation failure");
        assertThat(mapper(SetmealMapper.class).getById(1L).getName()).isEqualTo("单人套餐");
        assertThat(mapper(SetmealDishMapper.class).getBySetmealId(1L)).hasSize(2);
    }
    @Test void setmealDeleteFailureMustRollBackParentRow() {
        SetmealDishMapper relations=spy(transactionalMapper(SetmealDishMapper.class)); doThrow(new IllegalStateException("relation failure")).when(relations).batchDeleteBySetmealIds(any());
        assertThatThrownBy(() -> setmeals(relations).deleteWithDishes(Collections.singletonList(2L))).hasMessage("relation failure");
        assertThat(mapper(SetmealMapper.class).getById(2L)).isNotNull();
    }
}
