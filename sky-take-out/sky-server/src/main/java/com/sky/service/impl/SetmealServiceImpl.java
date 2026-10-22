package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    /**
     * 保存套餐信息，同时保存套餐和菜品的关联关系
     * 需要同步购物车中的数据
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        // 保存套餐信息
        setmealMapper.save(setmeal);
        // 获取并保存套餐对应的菜品关联关系
        Long setmealId = setmeal.getId();
        List<SetmealDish> dishes = setmealDTO.getSetmealDishes();
        if (dishes != null && !dishes.isEmpty()) {
            for  (SetmealDish setmealDish : dishes) {
                setmealDish.setSetmealId(setmealId);
            }
        }
        setmealDishMapper.batchInsert(dishes);

        updateShoppingCart(setmealId);
    }

    /**
     * 分页查询套餐信息
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据 id 查询套餐以及套餐对应的菜品信息
     * @param dishId
     * @return
     */
    @Override
    public SetmealVO getByIdWithDish(Long dishId) {
        SetmealVO setmealVO = new SetmealVO();
        // 获取套餐信息
        Setmeal setmeal = setmealMapper.getById(dishId);
        BeanUtils.copyProperties(setmeal, setmealVO);
        // 设置好套餐对应菜品与分类名称
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(dishId);
        setmealVO.setSetmealDishes(setmealDishes);
        setmealVO.setCategoryName(categoryMapper.getNameById(setmeal.getCategoryId()));
        return setmealVO;
    }

    /**
     * 修改套餐信息，同时修改套餐和菜品的关联关系
     * 需要同步更新购物车中对应的套餐数据
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void updateWithDishes(SetmealDTO setmealDTO) {
        // 更新套餐信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.update(setmeal);
        // 删除套餐对应的菜品关联关系
        setmealDishMapper.deleteBySetmealId(setmeal.getId());
        // 获取并保存套餐对应的新的菜品关联关系
        List<SetmealDish> dishes = setmealDTO.getSetmealDishes();
        if (dishes != null && !dishes.isEmpty()) {
            for (SetmealDish setmealDish : dishes) {
                setmealDish.setSetmealId(setmeal.getId());
            }
        }
        setmealDishMapper.batchInsert(dishes);

        // 同步更新购物车中对应的套餐数据
        updateShoppingCart(setmeal.getId());
    }

    /**
     * 起售或停售套餐
     * 停售需要同步购物车数据
     * @param status
     * @param id
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        Setmeal setmeal = new Setmeal();
        setmeal.setId(id);
        setmeal.setStatus(status);
        setmealMapper.update(setmeal);
        if (StatusConstant.DISABLE.equals(status)) {
            // 停售套餐时，需要同步更新购物车中对应的套餐数据
            updateShoppingCart(id);
        }

    }

    @Override
    @Transactional
    public void deleteWithDishes(List<Long> ids) {
        // 查询是否有起售中的套餐
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal.getStatus().equals(StatusConstant.ENABLE)) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        // 批量删除套餐信息
        setmealMapper.batchDeleteByIds(ids);
        // 批量删除套餐对应的菜品关联关系
        setmealDishMapper.batchDeleteBySetmealIds(ids);

        for (Long id : ids) {
            updateShoppingCart(id);
        }
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

    private void updateShoppingCart(Long setmealId) {
        Setmeal setmeal = setmealMapper.getById(setmealId);

        // 套餐被删除或被禁用，则删除购物车中对应的套餐数据
        if (setmeal == null || StatusConstant.DISABLE.equals(setmeal.getStatus())) {
            shoppingCartMapper.deleteBySetmealId(setmealId);
            return;
        }

        // 同步更新购物车中对应的套餐数据
        shoppingCartMapper.updateBySetmeal(setmeal);
    }
}
