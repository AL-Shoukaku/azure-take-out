package com.azure.controller.admin;

import com.azure.dto.coupon.CouponDTO;
import com.azure.result.Result;
import com.azure.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/coupon")
@Slf4j
public class CouponController {

    @Autowired
    private CouponService couponService;

    @RequestMapping("/publish")
    public Result<Object> publishCoupon(@RequestBody CouponDTO couponDTO) {
        log.info("发布优惠券：{}", couponDTO);
        couponService.publishCoupon(couponDTO);
        return Result.success();
    }
}
