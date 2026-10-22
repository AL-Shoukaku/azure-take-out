package com.azure.service;

import com.azure.annotation.AutoFill;
import com.azure.dto.DishDTO;
import com.azure.dto.DishPageQueryDTO;
import com.azure.entity.Dish;
import com.azure.enumeration.OperationType;
import com.azure.result.PageResult;
import com.azure.vo.DishVO;

import java.util.List;

public interface DishService {

    /**
     * 新增菜品和对应的口味数据
     * @param dishDTO
     */
    void saveWithFlavor(DishDTO dishDTO);

    /**
     * 分页查询菜品
     * @param dishPageQueryDTO
     * @return
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 批量删除菜品
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 根据id查询菜品信息和对应的口味信息
     * @param id
     * @return
     */
    DishVO getByIdWithFlavor(Long id);

    /**
     * 更新菜品和对应的口味数据
     */
    void updateWithFlavor(DishDTO dishDTO);

    /**
     * 起售或停售菜品
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    List<Dish> queryWithCategoryId(Long categoryId);

    /**
     * 条件查询菜品和口味
     * @return
     */
    List<DishVO> listWithFlavorByCategoryId(Long categoryId);
}
