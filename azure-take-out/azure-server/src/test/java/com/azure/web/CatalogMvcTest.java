package com.azure.web;

import com.azure.entity.*;
import com.azure.exception.BaseException;
import com.azure.result.PageResult;
import com.azure.support.WebTestSupport;
import com.azure.vo.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogMvcTest extends WebTestSupport {
    @Test void dishCreateAndUpdateBindFlavorsAndMoney() throws Exception {
        String body = "{\"id\":7,\"name\":\"鸡丁\",\"categoryId\":3,\"price\":12.50,\"status\":0,\"flavors\":[{\"name\":\"辣度\",\"value\":\"[]\"}]}";
        successful(admin(post("/admin/dish").contentType("application/json").content(body)));
        successful(admin(put("/admin/dish").contentType("application/json").content(body)));
        verify(dishService).saveWithFlavor(argThat(dto -> dto.getPrice().toPlainString().equals("12.50") && dto.getFlavors().size()==1));
        verify(dishService).updateWithFlavor(argThat(dto -> dto.getId()==7L && dto.getFlavors().get(0).getName().equals("辣度")));
    }
    @Test void dishPageBindsAllFilters() throws Exception {
        DishVO row = new DishVO(); row.setName("鸡丁"); row.setCategoryName("热菜");
        when(dishService.pageQuery(any())).thenReturn(new PageResult(19,Collections.singletonList(row)));
        successful(admin(get("/admin/dish/page").param("page","2").param("pageSize","5").param("name","鸡").param("categoryId","3").param("status","1")))
                .andExpect(jsonPath("$.data.total").value(19)).andExpect(jsonPath("$.data.records[0].categoryName").value("热菜"));
        verify(dishService).pageQuery(argThat(dto -> dto.getPage()==2 && dto.getPageSize()==5 && dto.getCategoryId()==3 && dto.getStatus()==1 && dto.getName().equals("鸡")));
    }
    @Test void dishDeleteBindsCommaSeparatedIdsAndStatus() throws Exception {
        successful(admin(delete("/admin/dish").param("ids","7,8"))); verify(dishService).batchDelete(Arrays.asList(7L,8L));
        successful(admin(post("/admin/dish/status/0").param("id","7"))); verify(dishService).startOrStop(0,7L);
    }
    @Test void dishDetailReturnsFlavors() throws Exception {
        DishVO row = new DishVO(); row.setId(7L); row.setFlavors(Collections.singletonList(DishFlavor.builder().name("辣度").build()));
        when(dishService.getByIdWithFlavor(7L)).thenReturn(row);
        successful(admin(get("/admin/dish/7"))).andExpect(jsonPath("$.data.flavors[0].name").value("辣度"));
    }
    @Test void dishListsUseDifferentServiceViews() throws Exception {
        when(dishService.queryWithCategoryId(3L)).thenReturn(Collections.singletonList(Dish.builder().id(7L).build()));
        when(dishService.listWithFlavorByCategoryId(3L)).thenReturn(Collections.singletonList(new DishVO()));
        successful(admin(get("/admin/dish/list").param("categoryId","3"))).andExpect(jsonPath("$.data[0].id").value(7));
        successful(user(get("/user/dish/list").param("categoryId","3"))).andExpect(jsonPath("$.data[0].flavors").isArray());
        verify(dishService).queryWithCategoryId(3L); verify(dishService).listWithFlavorByCategoryId(3L);
    }
    @Test void setmealCreateAndUpdateBindRelations() throws Exception {
        String body = "{\"id\":7,\"name\":\"套餐\",\"categoryId\":3,\"price\":29.90,\"setmealDishes\":[{\"dishId\":8,\"copies\":2}]}";
        successful(admin(post("/admin/setmeal").contentType("application/json").content(body)));
        successful(admin(put("/admin/setmeal").contentType("application/json").content(body)));
        verify(setmealService).saveWithDish(argThat(dto -> dto.getSetmealDishes().get(0).getDishId()==8L && dto.getSetmealDishes().get(0).getCopies()==2));
        verify(setmealService).updateWithDishes(argThat(dto -> dto.getId()==7L && dto.getPrice().toPlainString().equals("29.90")));
    }
    @Test void setmealPageBindsFiltersAndDetailReturnsRelations() throws Exception {
        when(setmealService.pageQuery(any())).thenReturn(new PageResult(0,Collections.emptyList()));
        successful(admin(get("/admin/setmeal/page").param("page","1").param("pageSize","10").param("name","套餐").param("categoryId","3").param("status","0")));
        verify(setmealService).pageQuery(argThat(dto -> dto.getCategoryId()==3 && dto.getStatus()==0 && dto.getName().equals("套餐")));
        SetmealVO row = new SetmealVO(); row.setId(7L); row.setSetmealDishes(Collections.singletonList(SetmealDish.builder().dishId(8L).build()));
        when(setmealService.getByIdWithDish(7L)).thenReturn(row);
        successful(admin(get("/admin/setmeal/7"))).andExpect(jsonPath("$.data.setmealDishes[0].dishId").value(8));
    }
    @Test void setmealDeleteAndStatusBindIds() throws Exception {
        successful(admin(delete("/admin/setmeal").param("ids","7,8"))); verify(setmealService).deleteWithDishes(Arrays.asList(7L,8L));
        successful(admin(post("/admin/setmeal/status/1").param("id","7"))); verify(setmealService).startOrStop(1,7L);
    }
    @Test void setmealListForcesOnSaleAndRepeatedReadsUseCache() throws Exception {
        when(setmealService.list(any())).thenReturn(Collections.singletonList(Setmeal.builder().id(7L).status(1).build()));
        successful(user(get("/user/setmeal/list").param("categoryId","3"))).andExpect(jsonPath("$.data[0].id").value(7));
        successful(user(get("/user/setmeal/list").param("categoryId","3")));
        verify(setmealService,times(1)).list(argThat(row -> row.getCategoryId()==3L && row.getStatus()==1));
    }
    @Test void setmealUpdateEvictsListCacheAndSubsequentReadReloads() throws Exception {
        when(setmealService.list(any())).thenReturn(Collections.singletonList(Setmeal.builder().name("旧名").build()),Collections.singletonList(Setmeal.builder().name("新名").build()));
        successful(user(get("/user/setmeal/list").param("categoryId","3"))).andExpect(jsonPath("$.data[0].name").value("旧名"));
        successful(admin(put("/admin/setmeal").contentType("application/json").content("{\"id\":7}")));
        successful(user(get("/user/setmeal/list").param("categoryId","3"))).andExpect(jsonPath("$.data[0].name").value("新名"));
        verify(setmealService,times(2)).list(any());
    }
    @Test void setmealDishItemsAreCachedBySetmealId() throws Exception {
        DishItemVO row = new DishItemVO(); row.setName("鸡丁"); row.setCopies(2);
        when(setmealService.getDishItemById(7L)).thenReturn(Collections.singletonList(row));
        successful(user(get("/user/setmeal/dish/7"))).andExpect(jsonPath("$.data[0].copies").value(2));
        successful(user(get("/user/setmeal/dish/7"))); verify(setmealService,times(1)).getDishItemById(7L);
    }
    @Test void setmealUpdateReloadsItsDetailsWithoutInvalidatingOtherSetmeals() throws Exception {
        DishItemVO old = new DishItemVO(); old.setName("旧菜品");
        DishItemVO updated = new DishItemVO(); updated.setName("新菜品");
        DishItemVO other = new DishItemVO(); other.setName("其他套餐菜品");
        when(setmealService.getDishItemById(7L)).thenReturn(Collections.singletonList(old),Collections.singletonList(updated));
        when(setmealService.getDishItemById(8L)).thenReturn(Collections.singletonList(other));
        successful(user(get("/user/setmeal/dish/7"))).andExpect(jsonPath("$.data[0].name").value("旧菜品"));
        successful(user(get("/user/setmeal/dish/8"))).andExpect(jsonPath("$.data[0].name").value("其他套餐菜品"));
        successful(admin(put("/admin/setmeal").contentType("application/json")
                .content("{\"id\":7,\"setmealDishes\":[{\"dishId\":9,\"copies\":1}]}")));
        successful(user(get("/user/setmeal/dish/7"))).andExpect(jsonPath("$.data[0].name").value("新菜品"));
        successful(user(get("/user/setmeal/dish/8"))).andExpect(jsonPath("$.data[0].name").value("其他套餐菜品"));
        verify(setmealService,times(2)).getDishItemById(7L);
        verify(setmealService,times(1)).getDishItemById(8L);
    }
    @Test void failedSetmealUpdateKeepsOriginalListAndDishCache() throws Exception {
        when(setmealService.list(any())).thenReturn(Collections.singletonList(Setmeal.builder().id(7L).name("原套餐").build()));
        DishItemVO original = new DishItemVO(); original.setName("原菜品");
        when(setmealService.getDishItemById(7L)).thenReturn(Collections.singletonList(original));
        successful(user(get("/user/setmeal/list").param("categoryId","3"))).andExpect(jsonPath("$.data[0].name").value("原套餐"));
        successful(user(get("/user/setmeal/dish/7"))).andExpect(jsonPath("$.data[0].name").value("原菜品"));
        doThrow(new BaseException("修改失败")).when(setmealService).updateWithDishes(any());
        mvc.perform(admin(put("/admin/setmeal").contentType("application/json").content("{\"id\":7}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        successful(user(get("/user/setmeal/list").param("categoryId","3"))).andExpect(jsonPath("$.data[0].name").value("原套餐"));
        successful(user(get("/user/setmeal/dish/7"))).andExpect(jsonPath("$.data[0].name").value("原菜品"));
        verify(setmealService,times(1)).list(any());
        verify(setmealService,times(1)).getDishItemById(7L);
    }
}
