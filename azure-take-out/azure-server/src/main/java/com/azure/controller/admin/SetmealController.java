package com.azure.controller.admin;

import com.azure.dto.SetmealDTO;
import com.azure.dto.SetmealPageQueryDTO;
import com.azure.result.PageResult;
import com.azure.result.Result;
import com.azure.service.SetmealService;
import com.azure.vo.SetmealVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/admin/setmeal")
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    /**
     * 新增套餐,新增时默认停售，不用更新缓存
     */
    @PostMapping
    @Operation(summary = "新增套餐")
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO);
        setmealService.saveWithDish(setmealDTO);
        return Result.success();
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "套餐分页查询")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("套餐分页查询，参数为{}",setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据 id 查询套餐以及套餐对应的菜品信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询套餐信息")
    public Result<SetmealVO> get(@PathVariable Long id) {
        log.info("根据id查询套餐信息，id为{}", id);
        SetmealVO setmealVO = setmealService.getByIdWithDish(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐信息
     * @param setmealDTO
     * @return
     */
    @PutMapping
    @Operation(summary = "修改套餐信息")
    public Result update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐信息，参数为{}", setmealDTO);
        setmealService.updateWithDishes(setmealDTO);
        return Result.success();
    }

    /**
     * 启用或禁用套餐
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "启用或禁用套餐")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("启用或禁用套餐，参数为status={}, id={}", status, id);
        setmealService.startOrStop(status, id);
        return Result.success();
    }

    @DeleteMapping
    @Operation(summary = "删除套餐以及套餐和菜品的关联关系")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("删除套餐，id={}", ids);
        setmealService.deleteWithDishes(ids);
        return Result.success();
    }
}
