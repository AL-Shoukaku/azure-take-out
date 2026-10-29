package com.azure.service.impl;

import com.azure.cache.CacheClient;
import com.azure.cache.RedisConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.azure.constant.MessageConstant;
import com.azure.constant.StatusConstant;
import com.azure.dto.DishDTO;
import com.azure.dto.DishPageQueryDTO;
import com.azure.entity.Dish;
import com.azure.entity.DishFlavor;
import com.azure.exception.DeletionNotAllowedException;
import com.azure.mapper.DishFlavorMapper;
import com.azure.mapper.DishMapper;
import com.azure.mapper.SetmealDishMapper;
import com.azure.mapper.ShoppingCartMapper;
import com.azure.result.PageResult;
import com.azure.service.DishService;
import com.azure.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setMealDishMapper;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    /**
     * 新增菜品，同时保存对应的口味数据，新增菜品不涉及购物车数据同步
     * @param dishDTO   菜品信息
     */
    @Override
    @Transactional  // 需要处理多个表，保证原子性
    public void saveWithFlavor(DishDTO dishDTO) {
        // 向菜品表插入一条数据
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.Insert(dish);
        // 向口味表插入多个数据
        Long dishId = dish.getId();  // 获取insert语句生成的主键值
        // 设置好每一个口味的 dishId
        for (DishFlavor flavor : dishDTO.getFlavors()) {
            flavor.setDishId(dishId);
        }
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            dishFlavorMapper.insertBatch(flavors);
        }
        // 清空对应分类的菜品缓存（事务提交后执行）
        cacheClient.deleteByKeyAfterCommit(RedisConstants.DISH_KEY_PREFIX + dish.getCategoryId());
    }

    /**
     * 分页查询菜品
     * @param dishPageQueryDTO  前端传来的分页查询参数
     * @return                  分页查询结果
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        // 执行分页查询,返回类型为 vo，因为相较于 dish 多了 categoryId 字段
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除菜品
     * 涉及多表操作，需要加 Transactional
     * 删除时需要同步购物车中的数据
     */
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        // 判断是否有起售中的菜品，有则无法删除
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish.getStatus() == 1) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        // 判断是否有与套餐关联的菜品，有则无法删除
        List<Long> setMealIds = setMealDishMapper.getSetMealIdsByDishIds(ids);
        if (setMealIds != null && !setMealIds.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        // 删除 dish 中的菜品数据以及 dish_flavor 中的口味数据
        // 使用批量删除方法
        dishMapper.batchDeleteByIds(ids);
        dishFlavorMapper.batchDeleteByDishIds(ids);

        // 同步购物车中的菜品数据，删除购物车中对应的菜品
        for (Long id : ids) {
            updateShoppingCart(id);
        }

        // 清空所有菜品缓存（事务提交后执行）
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.DISH_KEY_PREFIX);
    }

    /**
     * 根据 id 查询菜品信息和对应的口味信息
     * @param id    要查询的菜品 id
     * @return      封装了菜品信息和口味信息的 DishVO 对象
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        // 查询菜品信息
        Dish dish = dishMapper.getById(id);
        // 查询口味信息
        List<DishFlavor> flavors = dishFlavorMapper.getByDishId(id);
        // 封装成 DishVO 返回
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(flavors);
        return dishVO;
    }

    /**
     * 更新菜品信息和对应的口味信息
     * 需要同步更新购物车中的数据
     * @param dishDTO   菜品信息和口味信息的 DTO 对象
     */
    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        List<DishFlavor> flavors = dishDTO.getFlavors();
        // 更新菜品表
        dishMapper.update(dish);
        // 删除原有口味数据,插入新的口味数据
        dishFlavorMapper.deleteByDishId(dish.getId());
        if (flavors != null && flavors.size() > 0) {
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dish.getId());
            }
            dishFlavorMapper.insertBatch(flavors);
        }
        // 更新购物车
        updateShoppingCart(dish.getId());

        // 清空所有菜品缓存（事务提交后执行）
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.DISH_KEY_PREFIX);
    }

    /**
     * 起售或停售菜品
     * 需要同步更新购物车中的数据
     * @param status    菜品状态，1表示起售，0表示停售
     * @param id        菜品 id
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setStatus(status);
        dishMapper.update(dish);
        if (StatusConstant.DISABLE.equals(status)) {
            // 如果是停售，则需要删除购物车中对应的菜品
            updateShoppingCart(id);
        }
        // 清空所有菜品缓存（事务提交后执行）
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.DISH_KEY_PREFIX);
    }

    /**
     * 根据分类id查询菜品
     */
    @Override
    public List<Dish> queryWithCategoryId(Long categoryId) {
        return dishMapper.queryWithCategoryId(categoryId);
    }

    /**
     * 根据类型 id 查询对应的**起售**的菜品列表，同时查询对应的口味信息
     */
    @Override
    public List<DishVO> listWithFlavorByCategoryId(Long categoryId) {
        // 首先查询是否在缓存中有数据
        String key = RedisConstants.DISH_KEY_PREFIX + categoryId;
        return cacheClient.getOrLoad(key, new TypeReference<List<DishVO>>() {},
                () -> loadDishVOListByCategoryId(categoryId), RedisConstants.CACHE_DISH_TTL);
    }

    private List<DishVO> loadDishVOListByCategoryId(Long categoryId) {
        List<DishVO> dishVOList = new ArrayList<>();
        List<Dish> dishList = dishMapper.queryWithCategoryId(categoryId);
        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);
            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());
            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }
        return dishVOList;
    }

    /**
     * 根据菜品 id 更新购物车中的数据
     * @param DishId
     */
    private void updateShoppingCart(Long DishId) {
        Dish dish = dishMapper.getById(DishId);
        // 菜品被删除或者停售
        if (dish == null || StatusConstant.DISABLE.equals(dish.getStatus())) {
            shoppingCartMapper.deleteByDishId(DishId);
            return;
        }
        shoppingCartMapper.updateByDish(dish);
    }
}
