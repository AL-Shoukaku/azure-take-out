package com.azure.controller.admin;

import com.azure.dto.OrdersCancelDTO;
import com.azure.dto.OrdersConfirmDTO;
import com.azure.dto.OrdersPageQueryDTO;
import com.azure.dto.OrdersRejectionDTO;
import com.azure.result.PageResult;
import com.azure.result.Result;
import com.azure.service.OrdersService;
import com.azure.vo.OrderStatisticsVO;
import com.azure.vo.OrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.PeriodUnit;
import org.springframework.web.bind.annotation.*;

@RestController("AdminOrderController")
@RequestMapping("/admin/order")
@Slf4j
@Tag(name = "管理端订单相关接口")
public class OrderController {

    @Autowired
    private OrdersService ordersService;

    @GetMapping("/conditionSearch")
    @Operation(summary = "商家条件查询订单")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("商家条件查询订单: {}", ordersPageQueryDTO);
        PageResult pageResult = ordersService.adminPageQuery(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/statistics")
    @Operation(summary = "商家统计各个类别的订单")
    public Result<OrderStatisticsVO> statistics() {
        log.info("商家统计各个类别的订单");
        OrderStatisticsVO orderStatisticsVO = ordersService.statistics();
        return Result.success(orderStatisticsVO);
    }

    @PutMapping("/cancel")
    @Operation(summary = "商家取消订单")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("商家取消订单: {}", ordersCancelDTO);
        ordersService.cancelOrder(ordersCancelDTO);
        return  Result.success();
    }

    @PutMapping("/complete/{id}")
    @Operation(summary = "商家完成订单")
    public Result complete(@PathVariable Long id) {
        log.info("商家完成订单: {}", id);
        ordersService.completeOrder(id);
        return Result.success();
    }

    /**
     * 商家接单
     */
    @PutMapping("/confirm")
    @Operation(summary = "商家接单")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        log.info("商家接单: {}", ordersConfirmDTO);
        ordersService.confirmOrder(ordersConfirmDTO);
        return Result.success();
    }

    /**
     * 商家拒单，需要传入拒单原因
     */
    @PutMapping("/rejection")
    @Operation(summary = "商家拒单")
    public Result rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO) {
        log.info("商家拒单: {}", ordersRejectionDTO);
        ordersService.rejectOrder(ordersRejectionDTO);
        return Result.success();
    }

    @PutMapping("delivery/{id}")
    @Operation(summary = "商家派送订单")
    public Result delivery(@PathVariable Long id) {
        log.info("商家派送订单: {}", id);
        ordersService.deliveryOrder(id);
        return Result.success();
    }

    @GetMapping("/details/{id}")
    @Operation(summary = "商家查询订单详情")
    public Result<OrderVO> queryOrderDetail(@PathVariable Long id) {
        log.info("商家查询订单详情: {}", id);
        OrderVO orderVO = ordersService.queryOrderDetail(id);
        return Result.success(orderVO);
    }
}
