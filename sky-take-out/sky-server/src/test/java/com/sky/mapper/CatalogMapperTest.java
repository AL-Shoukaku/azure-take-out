package com.sky.mapper;

import com.github.pagehelper.*;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.support.SqlTestSupport;
import com.sky.vo.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CatalogMapperTest extends SqlTestSupport {
    @Test void categoryListExcludesDisabledAndFiltersType() {
        CategoryMapper mapper = mapper(CategoryMapper.class);
        assertThat(mapper.list(null)).extracting(Category::getId).containsExactly(1L,3L);
        assertThat(mapper.list(2)).extracting(Category::getId).containsExactly(3L);
        assertThat(mapper.getNameById(1L)).isEqualTo("热菜");
    }
    @Test void categoryCrudAndSearch() {
        CategoryMapper mapper = mapper(CategoryMapper.class); mapper.insert(Category.builder().type(1).name("新分类").sort(0).status(0).build());
        CategoryPageQueryDTO dto = new CategoryPageQueryDTO(); dto.setName("新"); dto.setType(1);
        PageHelper.startPage(1,10); Category saved = mapper.pageQuery(dto).get(0); assertThat(saved.getStatus()).isZero();
        mapper.update(Category.builder().id(saved.getId()).name("改名分类").status(1).build()); assertThat(mapper.getNameById(saved.getId())).isEqualTo("改名分类");
        mapper.deleteById(saved.getId()); assertThat(mapper.getNameById(saved.getId())).isNull();
    }
    @Test void dishCategoryListOnlyReturnsOnSaleAndSortsByUpdateTime() {
        assertThat(mapper(DishMapper.class).queryWithCategoryId(1L)).extracting(Dish::getId).containsExactly(2L,1L);
        assertThat(mapper(DishMapper.class).countByCategoryId(1L)).isEqualTo(3);
    }
    @Test void dishPageCombinesNameCategoryStatusAndCategoryName() {
        DishPageQueryDTO dto = new DishPageQueryDTO(); dto.setName("菜品"); dto.setCategoryId(1); dto.setStatus(0);
        PageHelper.startPage(1,10); Page<DishVO> result = mapper(DishMapper.class).pageQuery(dto);
        assertThat(result.getTotal()).isEqualTo(1); assertThat(result.get(0).getCategoryName()).isEqualTo("热菜");
    }
    @Test void dishGeneratedKeyPartialUpdateAndBatchDelete() {
        DishMapper mapper = mapper(DishMapper.class); Dish row = Dish.builder().name("新菜").categoryId(1L).price(BigDecimal.TEN).status(0).build();
        mapper.Insert(row); assertThat(row.getId()).isNotNull(); mapper.update(Dish.builder().id(row.getId()).name("改名菜").status(1).build());
        assertThat(mapper.getById(row.getId()).getPrice()).isEqualByComparingTo("10.00");
        assertThat(mapper.getById(row.getId()).getName()).isEqualTo("改名菜"); mapper.batchDeleteByIds(Collections.singletonList(row.getId())); assertThat(mapper.getById(row.getId())).isNull();
    }
    @Test void flavorBatchInsertQueryAndDelete() {
        DishFlavorMapper mapper = mapper(DishFlavorMapper.class); mapper.insertBatch(Arrays.asList(DishFlavor.builder().dishId(2L).name("辣度").value("[]").build(),DishFlavor.builder().dishId(3L).name("甜度").value("[]").build()));
        assertThat(mapper.getByDishId(2L)).extracting(DishFlavor::getName).containsExactly("辣度"); mapper.deleteByDishId(2L); assertThat(mapper.getByDishId(2L)).isEmpty();
        mapper.batchDeleteByDishIds(Arrays.asList(1L,3L)); assertThat(mapper.getByDishId(1L)).isEmpty(); assertThat(mapper.getByDishId(3L)).isEmpty();
    }
    @Test void setmealPageAndListFilterStoppedItems() {
        SetmealPageQueryDTO dto = new SetmealPageQueryDTO(); dto.setName("停售"); dto.setCategoryId(3); dto.setStatus(0);
        PageHelper.startPage(1,10); Page<SetmealVO> result = mapper(SetmealMapper.class).pageQuery(dto);
        assertThat(result.getTotal()).isEqualTo(1); assertThat(result.get(0).getCategoryName()).isEqualTo("套餐分类");
        assertThat(mapper(SetmealMapper.class).list(Setmeal.builder().categoryId(3L).status(1).build())).extracting(Setmeal::getId).containsExactly(1L);
    }
    @Test void setmealAndRelationsCrudUseGeneratedKey() {
        SetmealMapper mapper = mapper(SetmealMapper.class); SetmealDishMapper relations = mapper(SetmealDishMapper.class);
        Setmeal row = Setmeal.builder().name("新套餐").categoryId(3L).price(BigDecimal.TEN).status(0).build(); mapper.save(row); assertThat(row.getId()).isNotNull();
        relations.batchInsert(Collections.singletonList(SetmealDish.builder().setmealId(row.getId()).dishId(1L).name("鸡丁").price(new BigDecimal("12.50")).copies(2).build()));
        assertThat(relations.getBySetmealId(row.getId())).extracting(SetmealDish::getDishId).containsExactly(1L);
        assertThat(relations.getSetMealIdsByDishIds(Collections.singletonList(1L))).contains(row.getId(),1L);
        mapper.update(Setmeal.builder().id(row.getId()).price(new BigDecimal("20.00")).build()); assertThat(mapper.getById(row.getId()).getName()).isEqualTo("新套餐");
        relations.batchDeleteBySetmealIds(Collections.singletonList(row.getId())); mapper.batchDeleteByIds(Collections.singletonList(row.getId()));
        assertThat(mapper.getById(row.getId())).isNull(); assertThat(relations.getBySetmealId(row.getId())).isEmpty();
    }
    @Test void setmealDishItemsJoinDishImagesAndDescription() {
        List<DishItemVO> result = mapper(SetmealMapper.class).getDishItemBySetmealId(1L);
        assertThat(result).extracting(DishItemVO::getName).containsExactly("鸡丁","米饭");
        assertThat(result.get(0).getCopies()).isEqualTo(2); assertThat(result.get(0).getImage()).isEqualTo("dish.png"); assertThat(result.get(0).getDescription()).isEqualTo("鸡丁说明");
    }
    @Test void overviewCountsFilterStatusAndCategory() {
        Map<String,Object> filter = new HashMap<>(); filter.put("status",0); filter.put("categoryId",1L);
        assertThat(mapper(DishMapper.class).countByMap(filter)).isEqualTo(1);
        filter.put("categoryId",3L); assertThat(mapper(SetmealMapper.class).countByMap(filter)).isEqualTo(1);
        assertThat(mapper(SetmealMapper.class).countByCategoryId(3L)).isEqualTo(2);
    }
}
