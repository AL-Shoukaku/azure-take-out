package com.azure.controller.admin;

import com.azure.dto.coupon.CouponDTO;
import com.azure.dto.coupon.CouponPageQueryDTO;
import com.azure.dto.coupon.CouponUpdateDTO;
import com.azure.result.PageResult;
import com.azure.result.Result;
import com.azure.service.CouponService;
import com.azure.vo.coupon.CouponVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/coupon")
@Slf4j
public class CouponController {

    @Autowired
    private CouponService couponService;

    @PostMapping("/publish")
    @Operation(summary = "商家发布优惠券")
    public Result<Object> publishCoupon(@RequestBody @Valid CouponDTO couponDTO) {
        log.info("发布优惠券：{}", couponDTO);
        couponService.publishCoupon(couponDTO);
        return Result.success();
    }

    @PutMapping("/update")
    @Operation(summary = "商家修改优惠券信息")
    public Result<Object> updateCoupon(@RequestBody @Valid CouponUpdateDTO couponUpdateDTO) {
        log.info("商家修改优惠券信息：{}", couponUpdateDTO);
        couponService.updateCoupon(couponUpdateDTO);
        return Result.success();
    }

    @GetMapping("/{couponId}")
    @Operation(summary = "查询优惠券详情")
    public Result<CouponVO> getCouponDetails(@PathVariable Long couponId) {
        log.info("根据 id 查询优惠券详情：couponId={}", couponId);
        CouponVO couponVo = couponService.getById(couponId);
        return Result.success(couponVo);
    }

    @PutMapping("/status")
    @Operation(summary = "商家起售/停售优惠券")
    public Result<Object> startOrStop(@RequestParam Long couponId, @RequestParam Integer status) {
        log.info("商家起售/停售优惠券：couponId={}, status={}", couponId, status);
        // 调用服务层方法进行起售/停售操作
        couponService.startOrStop(couponId, status);
        return Result.success();
    }

    @PostMapping("/pageQuery")
    @Operation(summary = "分页查询优惠券列表")
    public Result<PageResult> pageQuery(@RequestBody @Valid CouponPageQueryDTO couponPageQueryDTO) {
        log.info("分页查询优惠券列表：{}", couponPageQueryDTO);
        PageResult pageResult = couponService.pageQuery(couponPageQueryDTO);
        return Result.success(pageResult);
    }

}
