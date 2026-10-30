package com.azure.service.impl;

import com.azure.cache.CacheClient;
import com.azure.cache.RedisConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.azure.constant.MessageConstant;
import com.azure.constant.StatusConstant;
import com.azure.dto.SetmealDTO;
import com.azure.dto.SetmealPageQueryDTO;
import com.azure.entity.Setmeal;
import com.azure.entity.SetmealDish;
import com.azure.exception.DeletionNotAllowedException;
import com.azure.mapper.CategoryMapper;
import com.azure.mapper.SetmealDishMapper;
import com.azure.mapper.SetmealMapper;
import com.azure.mapper.ShoppingCartMapper;
import com.azure.result.PageResult;
import com.azure.service.SetmealService;
import com.azure.vo.DishItemVO;
import com.azure.vo.SetmealVO;
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

    @Autowired
    private CacheClient cacheClient;

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

        // 新套餐影响所属分类的套餐列表缓存（事务提交后执行）
        cacheClient.deleteByKeyAfterCommit(RedisConstants.SETMEAL_KEY_PREFIX + setmeal.getCategoryId());
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

        // 修改可能换了分类，且套餐内菜品也会变化，直接按前缀清空两类缓存（事务提交后执行）
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.SETMEAL_KEY_PREFIX);
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.SETMEALDISH_KEY_PREFIX);
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

        // 起售状态影响分类列表缓存（事务提交后执行）
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.SETMEAL_KEY_PREFIX);
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.SETMEALDISH_KEY_PREFIX);
    }

    /**
     * 删除套餐信息，同时删除套餐和菜品的关联关系
     * 需要同步更新购物车中对应的套餐数据
     * @param ids   要删除的套餐 id 列表
     */
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

        // 删除的套餐分布在不同分类，直接按前缀清空两类缓存（事务提交后执行）
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.SETMEAL_KEY_PREFIX);
        cacheClient.deleteByPrefixAfterCommit(RedisConstants.SETMEALDISH_KEY_PREFIX);
    }

    /**
     * 根据 setmeal 中的信息查询对应的套餐列表，包含分类 id 和起售状态
     * 用于用户端查询套餐，管理端不用
     * @param setmeal   套餐对象，包含分类 id 和起售状态
     * @return          查询出的曹参列表
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        String key = RedisConstants.SETMEAL_KEY_PREFIX + setmeal.getCategoryId();
        return cacheClient.getOrLoad(key, new TypeReference<List<Setmeal>>() {},
                () -> setmealMapper.list(setmeal), RedisConstants.CACHE_SETMEAL_TTL);
    }

    /**
     * 根据套餐 id 查询套餐中的菜品列表
     * 用于用户端查询套餐详情，管理端不用
     * @param id    套餐 id
     * @return      查询出的套餐中的菜品列表
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        String key = RedisConstants.SETMEALDISH_KEY_PREFIX + id;
        return cacheClient.getOrLoad(key, new TypeReference<List<DishItemVO>>() {},
                () -> setmealMapper.getDishItemBySetmealId(id), RedisConstants.CACHE_SETMEALDISH_TTL);
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
