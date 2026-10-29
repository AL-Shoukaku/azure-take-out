package com.azure.service.impl;

import com.azure.cache.CacheClient;
import com.azure.cache.RedisConstants;
import com.azure.constant.MessageConstant;
import com.azure.dto.*;
import com.azure.entity.*;
import com.azure.exception.DeletionNotAllowedException;
import com.azure.mapper.*;
import com.azure.result.PageResult;
import com.azure.support.ServiceTestSupport;
import com.azure.vo.DishVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes","unchecked"})
class DishServiceImplTest extends ServiceTestSupport {
    @InjectMocks private DishServiceImpl service;
    @Mock private DishMapper dishMapper;
    @Mock private DishFlavorMapper dishFlavorMapper;
    @Mock private SetmealDishMapper setMealDishMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private CacheClient cacheClient;
    @Captor private ArgumentCaptor<Dish> dish;

    private DishDTO request() {
        DishDTO dto = new DishDTO(); dto.setId(7L); dto.setName("宫保鸡丁"); dto.setCategoryId(3L);
        dto.setPrice(new BigDecimal("32.00")); dto.setStatus(0); return dto;
    }
    @Test void saveUsesGeneratedDishIdForAllFlavorsAndEvictsCategoryCache() {
        DishDTO dto = request();
        DishFlavor flavor = DishFlavor.builder().name("辣度").value("[\"微辣\"]").build();
        dto.setFlavors(Collections.singletonList(flavor));
        doAnswer(i -> { ((Dish)i.getArgument(0)).setId(88L); return null; }).when(dishMapper).Insert(any());
        service.saveWithFlavor(dto);
        verify(dishMapper).Insert(dish.capture());
        assertThat(dish.getValue().getName()).isEqualTo("宫保鸡丁");
        assertThat(dish.getValue().getPrice()).isEqualByComparingTo("32.00");
        assertThat(flavor.getDishId()).isEqualTo(88L);
        verify(dishFlavorMapper).insertBatch(dto.getFlavors());
        verify(cacheClient).deleteByKeyAfterCommit("dish:3");
    }
    @Test void saveWithoutFlavorsSkipsFlavorInsert() {
        service.saveWithFlavor(request()); verify(dishFlavorMapper,never()).insertBatch(any());
        verify(cacheClient).deleteByKeyAfterCommit("dish:3");
    }
    @Test void onSaleDishPreventsEntireBatchDeletion() {
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().status(0).build());
        when(dishMapper.getById(8L)).thenReturn(Dish.builder().status(1).build());
        assertThatThrownBy(() -> service.batchDelete(Arrays.asList(7L,8L))).isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage(MessageConstant.DISH_ON_SALE);
        verify(dishMapper,never()).batchDeleteByIds(any()); verifyNoInteractions(dishFlavorMapper,setMealDishMapper,cacheClient,shoppingCartMapper);
    }
    @Test void associatedDishPreventsDeletion() {
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().status(0).build());
        when(setMealDishMapper.getSetMealIdsByDishIds(Collections.singletonList(7L))).thenReturn(Collections.singletonList(9L));
        assertThatThrownBy(() -> service.batchDelete(Collections.singletonList(7L))).isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        verify(dishMapper,never()).batchDeleteByIds(any()); verifyNoInteractions(dishFlavorMapper,cacheClient,shoppingCartMapper);
    }
    @ParameterizedTest @NullAndEmptySource
    void unassociatedStoppedDishesDeleteFlavorsAndInvalidateAllDishCaches(List<Long> associations) {
        List<Long> ids = Arrays.asList(7L,8L);
        for (Long id : ids) {
            when(dishMapper.getById(id)).thenReturn(Dish.builder().id(id).status(0).build()).thenReturn(null);
        }
        when(setMealDishMapper.getSetMealIdsByDishIds(ids)).thenReturn(associations);
        service.batchDelete(ids); verify(dishMapper).batchDeleteByIds(ids);
        verify(dishFlavorMapper).batchDeleteByDishIds(ids);
        verify(cacheClient).deleteByPrefixAfterCommit(RedisConstants.DISH_KEY_PREFIX);
        verify(shoppingCartMapper).deleteByDishId(7L); verify(shoppingCartMapper).deleteByDishId(8L);
    }
    @Test void detailContainsDishAndFlavors() {
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().id(7L).name("宫保鸡丁").price(new BigDecimal("32.00")).build());
        DishFlavor flavor = DishFlavor.builder().dishId(7L).name("辣度").build();
        when(dishFlavorMapper.getByDishId(7L)).thenReturn(Collections.singletonList(flavor));
        DishVO result = service.getByIdWithFlavor(7L);
        assertThat(result.getName()).isEqualTo("宫保鸡丁"); assertThat(result.getPrice()).isEqualByComparingTo("32.00");
        assertThat(result.getFlavors()).containsExactly(flavor);
    }
    @Test void updateReplacesFlavorsAndInvalidatesCaches() {
        DishDTO dto = request(); DishFlavor flavor = DishFlavor.builder().name("甜度").build();
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().id(7L).status(0).build());
        dto.setFlavors(Collections.singletonList(flavor)); service.updateWithFlavor(dto);
        InOrder sequence = inOrder(dishMapper,dishFlavorMapper);
        sequence.verify(dishMapper).update(any()); sequence.verify(dishFlavorMapper).deleteByDishId(7L);
        sequence.verify(dishFlavorMapper).insertBatch(dto.getFlavors());
        assertThat(flavor.getDishId()).isEqualTo(7L); verify(cacheClient).deleteByPrefixAfterCommit(RedisConstants.DISH_KEY_PREFIX);
    }
    @ParameterizedTest @NullAndEmptySource
    void updateCanRemoveAllFlavors(List<DishFlavor> flavors) {
        when(dishMapper.getById(7L)).thenReturn(Dish.builder().id(7L).status(0).build());
        DishDTO dto = request(); dto.setFlavors(flavors); service.updateWithFlavor(dto);
        verify(dishFlavorMapper).deleteByDishId(7L); verify(dishFlavorMapper,never()).insertBatch(any());
    }
    @ParameterizedTest @ValueSource(ints = {0,1})
    void statusChangeInvalidatesAllDishCaches(int status) {
        if (status == 0) when(dishMapper.getById(7L)).thenReturn(Dish.builder().id(7L).status(0).build());
        service.startOrStop(status,7L); verify(dishMapper).update(dish.capture());
        assertThat(dish.getValue().getId()).isEqualTo(7L); assertThat(dish.getValue().getStatus()).isEqualTo(status);
        verify(cacheClient).deleteByPrefixAfterCommit(RedisConstants.DISH_KEY_PREFIX);
        if (status == 0) verify(shoppingCartMapper).deleteByDishId(7L);
        else verifyNoInteractions(shoppingCartMapper);
    }
    @Test void categoryListReturnsMapperResult() {
        Dish row = Dish.builder().id(7L).build();
        when(dishMapper.queryWithCategoryId(3L)).thenReturn(Collections.singletonList(row));
        assertThat(service.queryWithCategoryId(3L)).containsExactly(row);
    }
    @Test void pageReturnsCategoryEnrichedRows() {
        DishPageQueryDTO dto = new DishPageQueryDTO(); dto.setPage(1); dto.setPageSize(10);
        DishVO row = new DishVO(); row.setCategoryName("热菜");
        when(dishMapper.pageQuery(dto)).thenReturn(page(21,row));
        PageResult result = service.pageQuery(dto);
        assertThat(result.getTotal()).isEqualTo(21); assertThat(result.getRecords()).containsExactly(row);
    }
    @Test void cacheHitAvoidsDatabaseQueries() {
        List<DishVO> cached = Collections.singletonList(new DishVO());
        when(cacheClient.getOrLoad(eq("dish:3"), any(), any(), eq(RedisConstants.CACHE_DISH_TTL))).thenReturn(cached);
        assertThat(service.listWithFlavorByCategoryId(3L)).isSameAs(cached);
        verifyNoInteractions(dishMapper,dishFlavorMapper);
    }
    @Test void cacheMissDelegatesLoaderToMappersAndReturnsMappedResult() {
        when(cacheClient.getOrLoad(eq("dish:3"), any(), any(), eq(RedisConstants.CACHE_DISH_TTL)))
                .thenAnswer(inv -> ((Supplier<List<DishVO>>)inv.getArgument(2)).get());
        when(dishMapper.queryWithCategoryId(3L)).thenReturn(Collections.singletonList(Dish.builder().id(7L).name("宫保鸡丁").status(1).build()));
        DishFlavor flavor = DishFlavor.builder().dishId(7L).name("辣度").build();
        when(dishFlavorMapper.getByDishId(7L)).thenReturn(Collections.singletonList(flavor));
        List<DishVO> result = service.listWithFlavorByCategoryId(3L);
        assertThat(result).hasSize(1); assertThat(result.get(0).getName()).isEqualTo("宫保鸡丁");
        assertThat(result.get(0).getFlavors()).containsExactly(flavor);
    }
    @Test void emptyCategoryLoaderReturnsEmptyListWithoutFlavorQueries() {
        when(cacheClient.getOrLoad(eq("dish:3"), any(), any(), eq(RedisConstants.CACHE_DISH_TTL)))
                .thenAnswer(inv -> ((Supplier<List<DishVO>>)inv.getArgument(2)).get());
        when(dishMapper.queryWithCategoryId(3L)).thenReturn(Collections.emptyList());
        assertThat(service.listWithFlavorByCategoryId(3L)).isEmpty();
        verifyNoInteractions(dishFlavorMapper);
    }
}
