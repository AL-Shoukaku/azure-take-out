package com.azure.service.impl;

import com.azure.cache.CacheClient;
import com.azure.cache.RedisConstants;
import com.azure.constant.MessageConstant;
import com.azure.dto.*;
import com.azure.entity.Category;
import com.azure.exception.DeletionNotAllowedException;
import com.azure.mapper.*;
import com.azure.result.PageResult;
import com.azure.support.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes","unchecked"})
class CategoryServiceImplTest extends ServiceTestSupport {
    @InjectMocks private CategoryServiceImpl service;
    @Mock private CategoryMapper categoryMapper;
    @Mock private DishMapper dishMapper;
    @Mock private SetmealMapper setmealMapper;
    @Mock private CacheClient cacheClient;
    @Captor private ArgumentCaptor<Category> category;

    private CategoryDTO dto() {
        CategoryDTO dto = new CategoryDTO(); dto.setId(7L); dto.setName("热菜"); dto.setSort(3); dto.setType(1); return dto;
    }
    @Test void newCategoryStartsDisabled() {
        service.save(dto()); verify(categoryMapper).insert(category.capture());
        assertThat(category.getValue()).extracting(Category::getName, Category::getType, Category::getSort, Category::getStatus)
                .containsExactly("热菜", 1, 3, 0);
        verifyNoInteractions(cacheClient); // 新增分类默认禁用，不出现在起售列表中，无需清缓存
    }
    @Test void updateCopiesEditableFields() {
        service.update(dto()); verify(categoryMapper).update(category.capture());
        assertThat(category.getValue()).extracting(Category::getId, Category::getName, Category::getType, Category::getSort)
                .containsExactly(7L, "热菜", 1, 3);
        assertThat(category.getValue().getStatus()).isNull();
        verify(cacheClient).deleteByPrefixAfterCommit(RedisConstants.CATEGORY_KEY_PREFIX);
    }
    @Test void categoryWithDishCannotBeDeleted() {
        when(dishMapper.countByCategoryId(7L)).thenReturn(1);
        assertThatThrownBy(() -> service.deleteById(7L)).isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        verifyNoInteractions(setmealMapper, cacheClient); verify(categoryMapper, never()).deleteById(anyLong());
    }
    @Test void categoryWithSetmealCannotBeDeleted() {
        when(dishMapper.countByCategoryId(7L)).thenReturn(0);
        when(setmealMapper.countByCategoryId(7L)).thenReturn(2);
        assertThatThrownBy(() -> service.deleteById(7L)).isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);
        verify(categoryMapper, never()).deleteById(anyLong()); verifyNoInteractions(cacheClient);
    }
    @Test void emptyCategoryCanBeDeleted() {
        when(dishMapper.countByCategoryId(7L)).thenReturn(0);
        when(setmealMapper.countByCategoryId(7L)).thenReturn(0);
        service.deleteById(7L); verify(categoryMapper).deleteById(7L);
        verify(cacheClient).deleteByPrefixAfterCommit(RedisConstants.CATEGORY_KEY_PREFIX);
    }
    @ParameterizedTest @ValueSource(ints = {0,1})
    void statusChangeIncludesAuditFields(int status) {
        LocalDateTime before = LocalDateTime.now(); service.startOrStop(status, 7L);
        verify(categoryMapper).update(category.capture());
        assertThat(category.getValue().getStatus()).isEqualTo(status);
        assertThat(category.getValue().getId()).isEqualTo(7L);
        assertThat(category.getValue().getUpdateUser()).isEqualTo(USER_ID);
        assertThat(category.getValue().getUpdateTime()).isBetween(before, LocalDateTime.now());
        verify(cacheClient).deleteByPrefixAfterCommit(RedisConstants.CATEGORY_KEY_PREFIX);
    }
    @Test void pagePreservesTotalAndRecords() {
        CategoryPageQueryDTO dto = new CategoryPageQueryDTO(); dto.setPage(1); dto.setPageSize(10);
        Category row = Category.builder().id(7L).name("热菜").build();
        when(categoryMapper.pageQuery(dto)).thenReturn(page(31, row));
        PageResult result = service.pageQuery(dto);
        assertThat(result.getTotal()).isEqualTo(31); assertThat(result.getRecords()).containsExactly(row);
    }
    @Test void listSupportsOptionalTypeThroughCacheLoader() {
        Category row = Category.builder().id(7L).build();
        when(categoryMapper.list(null)).thenReturn(Collections.singletonList(row));
        when(cacheClient.getOrLoad(anyString(), any(), any(), eq(RedisConstants.CACHE_CATEGORY_TTL)))
                .thenAnswer(inv -> ((Supplier<List<Category>>)inv.getArgument(2)).get());
        assertThat(service.list(null)).containsExactly(row); // type 为 null 时 key 为 "category:null"
        service.list(2); verify(categoryMapper).list(2);
        verify(cacheClient).getOrLoad(eq("category:null"), any(), any(), anyLong());
        verify(cacheClient).getOrLoad(eq("category:2"), any(), any(), anyLong());
    }
}
