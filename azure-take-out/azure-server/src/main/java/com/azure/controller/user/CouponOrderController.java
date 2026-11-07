package com.azure.controller.user;

import com.azure.result.PageResult;
import com.azure.result.Result;
import com.azure.service.CouponOrderService;
import com.azure.vo.coupon.CouponOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userCouponOrderController")
@RequestMapping("/user/couponOrder")
@Slf4j
@Tag(name = "C端-优惠券订单相关接口")
public class CouponOrderController {

    @Autowired
    private CouponOrderService couponOrderService;

    @GetMapping("/historyOrders")
    @Operation(summary = "用户分页查询自己的优惠券订单")
    public Result<PageResult> historyOrders(int page, int pageSize, Integer status) {
        log.info("用户查询自己的优惠券订单，page：{}，pageSize：{}，status：{}", page, pageSize, status);
        PageResult pageResult = couponOrderService.historyOrdersPageQuery(page, pageSize, status);
        return Result.success(pageResult);
    }

    @GetMapping("/orderDetail/{id}")
    @Operation(summary = "用户查询优惠券订单详情")
    public Result<CouponOrderVO> orderDetail(@PathVariable Long id) {
        log.info("用户查询优惠券订单详情，订单id：{}", id);
        CouponOrderVO couponOrderVO = couponOrderService.queryUserOrderDetail(id);
        return Result.success(couponOrderVO);
    }
}
