package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.support.ServiceTestSupport;
import com.sky.vo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetmealServiceImplTest extends ServiceTestSupport {
    @InjectMocks private SetmealServiceImpl service;
    @Mock private SetmealMapper setmealMapper;
    @Mock private SetmealDishMapper setmealDishMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Captor private ArgumentCaptor<Setmeal> setmeal;

    private SetmealDTO request() {
        SetmealDTO dto = new SetmealDTO(); dto.setId(7L); dto.setName("午餐套餐"); dto.setCategoryId(3L);
        dto.setPrice(new BigDecimal("29.90")); dto.setStatus(0);
        dto.setSetmealDishes(Arrays.asList(SetmealDish.builder().dishId(8L).copies(2).build(),SetmealDish.builder().dishId(9L).copies(1).build()));
        return dto;
    }
    @Test void savePropagatesGeneratedIdToAllAssociations() {
        SetmealDTO dto = request();
        doAnswer(i -> { ((Setmeal)i.getArgument(0)).setId(88L); return null; }).when(setmealMapper).save(any());
        when(setmealMapper.getById(88L)).thenReturn(Setmeal.builder().id(88L).status(0).build());
        service.saveWithDish(dto); verify(setmealMapper).save(setmeal.capture());
        assertThat(setmeal.getValue().getName()).isEqualTo("午餐套餐");
        assertThat(setmeal.getValue().getPrice()).isEqualByComparingTo("29.90");
        assertThat(dto.getSetmealDishes()).extracting(SetmealDish::getSetmealId).containsExactly(88L,88L);
        verify(setmealDishMapper).batchInsert(dto.getSetmealDishes());
    }
    @Test void updateReplacesAssociations() {
        when(setmealMapper.getById(7L)).thenReturn(Setmeal.builder().id(7L).status(0).build());
        SetmealDTO dto = request(); service.updateWithDishes(dto);
        InOrder sequence = inOrder(setmealMapper,setmealDishMapper);
        sequence.verify(setmealMapper).update(any()); sequence.verify(setmealDishMapper).deleteBySetmealId(7L);
        sequence.verify(setmealDishMapper).batchInsert(dto.getSetmealDishes());
        assertThat(dto.getSetmealDishes()).extracting(SetmealDish::getSetmealId).containsExactly(7L,7L);
    }
    @Test void detailCombinesCategoryAndDishRelations() {
        Setmeal row = Setmeal.builder().id(7L).categoryId(3L).name("午餐套餐").build();
        List<SetmealDish> dishes = request().getSetmealDishes();
        when(setmealMapper.getById(7L)).thenReturn(row); when(setmealDishMapper.getBySetmealId(7L)).thenReturn(dishes);
        when(categoryMapper.getNameById(3L)).thenReturn("套餐分类");
        SetmealVO result = service.getByIdWithDish(7L);
        assertThat(result.getName()).isEqualTo("午餐套餐"); assertThat(result.getCategoryName()).isEqualTo("套餐分类");
        assertThat(result.getSetmealDishes()).isEqualTo(dishes);
    }
    @Test void onSaleSetmealPreventsEntireBatchDeletion() {
        when(setmealMapper.getById(7L)).thenReturn(Setmeal.builder().status(0).build());
        when(setmealMapper.getById(8L)).thenReturn(Setmeal.builder().status(1).build());
        assertThatThrownBy(() -> service.deleteWithDishes(Arrays.asList(7L,8L))).isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage(MessageConstant.SETMEAL_ON_SALE);
        verify(setmealMapper,never()).batchDeleteByIds(any()); verifyNoInteractions(setmealDishMapper,shoppingCartMapper);
    }
    @Test void stoppedSetmealsDeleteWithAssociations() {
        List<Long> ids = Arrays.asList(7L,8L);
        for (Long id : ids) {
            when(setmealMapper.getById(id)).thenReturn(Setmeal.builder().id(id).status(0).build()).thenReturn(null);
        }
        service.deleteWithDishes(ids); verify(setmealMapper).batchDeleteByIds(ids);
        verify(setmealDishMapper).batchDeleteBySetmealIds(ids);
        verify(shoppingCartMapper).deleteBySetmealId(7L); verify(shoppingCartMapper).deleteBySetmealId(8L);
    }
    @ParameterizedTest @ValueSource(ints = {0,1})
    void statusChangeUpdatesTarget(int status) {
        if (status == 0) when(setmealMapper.getById(7L)).thenReturn(Setmeal.builder().id(7L).status(0).build());
        service.startOrStop(status,7L); verify(setmealMapper).update(setmeal.capture());
        assertThat(setmeal.getValue().getId()).isEqualTo(7L); assertThat(setmeal.getValue().getStatus()).isEqualTo(status);
        if (status == 0) verify(shoppingCartMapper).deleteBySetmealId(7L);
        else verifyNoInteractions(shoppingCartMapper);
    }
    @Test void pagePreservesTotalAndRecords() {
        SetmealPageQueryDTO dto = new SetmealPageQueryDTO(); dto.setPage(1); dto.setPageSize(10);
        SetmealVO row = new SetmealVO(); row.setCategoryName("套餐分类");
        when(setmealMapper.pageQuery(dto)).thenReturn(page(21,row));
        PageResult result = service.pageQuery(dto);
        assertThat(result.getTotal()).isEqualTo(21); assertThat(result.getRecords()).containsExactly(row);
    }
    @Test void listForwardsCategoryAndStatusFilter() {
        Setmeal filter = Setmeal.builder().categoryId(3L).status(1).build();
        Setmeal row = Setmeal.builder().id(7L).build(); when(setmealMapper.list(filter)).thenReturn(Collections.singletonList(row));
        assertThat(service.list(filter)).containsExactly(row);
    }
    @Test void dishItemsIncludeCopiesImageAndDescription() {
        DishItemVO item = new DishItemVO(); item.setName("鸡丁"); item.setCopies(2); item.setImage("dish.png");
        when(setmealMapper.getDishItemBySetmealId(7L)).thenReturn(Collections.singletonList(item));
        assertThat(service.getDishItemById(7L)).containsExactly(item);
    }
}
