package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrdersService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("AdminOrderController")
@RequestMapping("/admin/order")
@Slf4j
@Api("管理端订单相关接口")
public class OrderController {

    @Autowired
    private OrdersService ordersService;

    @GetMapping("/conditionSearch")
    @ApiOperation("商家条件查询订单")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("商家条件查询订单: {}", ordersPageQueryDTO);
        PageResult pageResult = ordersService.adminPageQuery(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/statistics")
    @ApiOperation("商家统计各个类别的订单")
    public Result<OrderStatisticsVO> statistics() {
        log.info("商家统计各个类别的订单");
        OrderStatisticsVO orderStatisticsVO = ordersService.statistics();
        return Result.success(orderStatisticsVO);
    }

    @PutMapping("/cancel")
    @ApiOperation("商家取消订单")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("商家取消订单: {}", ordersCancelDTO);
        ordersService.cancelOrder(ordersCancelDTO);
        return  Result.success();
    }
}
