package com.sky.service;

import com.sky.dto.SetmealDTO;

public interface SetmealService {

    /**
     * 保存套餐信息，同时保存套餐和菜品的关联关系
     */
    void saveWithDish(SetmealDTO setmealDTO);
}
