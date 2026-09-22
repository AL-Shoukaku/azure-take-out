package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.result.Result;
import com.sky.service.OrdersService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("AdminOrderController")
@RequestMapping("/admin/order")
@Slf4j
@Api("管理端订单相关接口")
public class OrderController {

    private OrdersService ordersService;

    @PutMapping("/cancel")
    @ApiOperation("商家取消订单")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("商家取消订单: {}", ordersCancelDTO);
        ordersService.cancelOrder(ordersCancelDTO);
        return  Result.success();
    }
}
