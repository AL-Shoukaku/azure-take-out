package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.SetmealVO;

public interface SetmealService {

    /**
     * 保存套餐信息，同时保存套餐和菜品的关联关系
     */
    void saveWithDish(SetmealDTO setmealDTO);

    /**
     *  分页查询套餐信息
     */
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据 id 查询套餐以及套餐对应的菜品信息
     */
    SetmealVO getByIdWithDish(Long dishId);

    /**
     * 修改套餐信息，同时修改套餐和菜品的关联关系
     */
    void updateWithDishes(SetmealDTO setmealDTO);

    /**
     *  起售或停售套餐
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);
}
