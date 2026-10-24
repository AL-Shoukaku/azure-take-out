package com.azure.controller.user;

import com.azure.constant.StatusConstant;
import com.azure.entity.Dish;
import com.azure.result.Result;
import com.azure.service.DishService;
import com.azure.vo.DishVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Tag(name = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("用户端根据分类id查询菜品, categoryId: {}", categoryId);
        // 只查询起售的菜品
        List<DishVO> list = dishService.listWithFlavorByCategoryId(categoryId);
        return Result.success(list);
    }

}
