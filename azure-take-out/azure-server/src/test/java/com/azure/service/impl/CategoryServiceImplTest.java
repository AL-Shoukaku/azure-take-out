package com.azure.service.impl;

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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryServiceImplTest extends ServiceTestSupport {
    @InjectMocks private CategoryServiceImpl service;
    @Mock private CategoryMapper categoryMapper;
    @Mock private DishMapper dishMapper;
    @Mock private SetmealMapper setmealMapper;
    @Captor private ArgumentCaptor<Category> category;

    private CategoryDTO dto() {
        CategoryDTO dto = new CategoryDTO(); dto.setId(7L); dto.setName("热菜"); dto.setSort(3); dto.setType(1); return dto;
    }
    @Test void newCategoryStartsDisabled() {
        service.save(dto()); verify(categoryMapper).insert(category.capture());
        assertThat(category.getValue()).extracting(Category::getName, Category::getType, Category::getSort, Category::getStatus)
                .containsExactly("热菜", 1, 3, 0);
    }
    @Test void updateCopiesEditableFields() {
        service.update(dto()); verify(categoryMapper).update(category.capture());
        assertThat(category.getValue()).extracting(Category::getId, Category::getName, Category::getType, Category::getSort)
                .containsExactly(7L, "热菜", 1, 3);
        assertThat(category.getValue().getStatus()).isNull();
    }
    @Test void categoryWithDishCannotBeDeleted() {
        when(dishMapper.countByCategoryId(7L)).thenReturn(1);
        assertThatThrownBy(() -> service.deleteById(7L)).isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        verifyNoInteractions(setmealMapper); verify(categoryMapper, never()).deleteById(anyLong());
    }
    @Test void categoryWithSetmealCannotBeDeleted() {
        when(dishMapper.countByCategoryId(7L)).thenReturn(0);
        when(setmealMapper.countByCategoryId(7L)).thenReturn(2);
        assertThatThrownBy(() -> service.deleteById(7L)).isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);
        verify(categoryMapper, never()).deleteById(anyLong());
    }
    @Test void emptyCategoryCanBeDeleted() {
        when(dishMapper.countByCategoryId(7L)).thenReturn(0);
        when(setmealMapper.countByCategoryId(7L)).thenReturn(0);
        service.deleteById(7L); verify(categoryMapper).deleteById(7L);
    }
    @ParameterizedTest @ValueSource(ints = {0,1})
    void statusChangeIncludesAuditFields(int status) {
        LocalDateTime before = LocalDateTime.now(); service.startOrStop(status, 7L);
        verify(categoryMapper).update(category.capture());
        assertThat(category.getValue().getStatus()).isEqualTo(status);
        assertThat(category.getValue().getId()).isEqualTo(7L);
        assertThat(category.getValue().getUpdateUser()).isEqualTo(USER_ID);
        assertThat(category.getValue().getUpdateTime()).isBetween(before, LocalDateTime.now());
    }
    @Test void pagePreservesTotalAndRecords() {
        CategoryPageQueryDTO dto = new CategoryPageQueryDTO(); dto.setPage(1); dto.setPageSize(10);
        Category row = Category.builder().id(7L).name("热菜").build();
        when(categoryMapper.pageQuery(dto)).thenReturn(page(31, row));
        PageResult result = service.pageQuery(dto);
        assertThat(result.getTotal()).isEqualTo(31); assertThat(result.getRecords()).containsExactly(row);
    }
    @Test void listSupportsOptionalType() {
        Category row = Category.builder().id(7L).build();
        when(categoryMapper.list(null)).thenReturn(Collections.singletonList(row));
        assertThat(service.list(null)).containsExactly(row);
        service.list(2); verify(categoryMapper).list(2);
    }
}
