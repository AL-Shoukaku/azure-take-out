package com.azure.controller.admin;

import com.azure.dto.coupon.CouponOrderPageQueryDTO;
import com.azure.dto.coupon.CouponOrderRefundDTO;
import com.azure.result.PageResult;
import com.azure.result.Result;
import com.azure.service.CouponOrderService;
import com.azure.vo.coupon.CouponOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("adminCouponOrderController")
@RequestMapping("/admin/couponOrder")
@Slf4j
@Tag(name = "B端-优惠券订单相关接口")
public class CouponOrderController {

    @Autowired
    private CouponOrderService couponOrderService;

    @GetMapping("/conditionSearch")
    @Operation(summary = "商家条件查询优惠券订单")
    public Result<PageResult> conditionSearch(CouponOrderPageQueryDTO couponOrderPageQueryDTO) {
        log.info("商家条件查询优惠券订单: {}", couponOrderPageQueryDTO);
        PageResult pageResult = couponOrderService.adminPageQuery(couponOrderPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/details/{id}")
    @Operation(summary = "商家查询优惠券订单详情")
    public Result<CouponOrderVO> queryOrderDetail(@PathVariable Long id) {
        log.info("商家查询优惠券订单详情: {}", id);
        CouponOrderVO couponOrderVO = couponOrderService.queryOrderDetail(id);
        return Result.success(couponOrderVO);
    }


    @PutMapping("/refund")
    @Operation(summary = "商家退款优惠券订单")
    public Result<Void> refundCouponOrder(@RequestBody @Valid CouponOrderRefundDTO couponOrderRefundDTO) {
        log.info("商家退款优惠券订单: {}", couponOrderRefundDTO);
        couponOrderService.refundCouponOrder(couponOrderRefundDTO);
        return Result.success();
    }
}
