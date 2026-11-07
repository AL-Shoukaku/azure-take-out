package com.azure.controller.user;

import com.azure.result.Result;
import com.azure.service.CouponService;
import com.azure.vo.coupon.AvailableCouponVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/user/coupon")
@Slf4j
@RestController
public class CouponController {

    @Autowired
    private CouponService couponService;

    /**
     * 用户查询可领取的优惠券列表
     * 包括未开始的优惠券和正在进行中的优惠券，且库存大于0
     * @return  可领取/购买的优惠券列表
     */
    @GetMapping("/available")
    public Result<List<AvailableCouponVO>> queryAvailableCoupons() {
        log.info("查询可领取的优惠券列表");
        List<AvailableCouponVO> availableCoupons = couponService.queryAvailableCoupons();
        return Result.success(availableCoupons);
    }

    /**
     * 用户领取满减券
     * @param couponId  优惠券ID
     */
    @PostMapping("/get")
    public Result<Void> getCoupon(Long couponId) {
        log.info("用户领取满减券，优惠券ID：{}", couponId);

        return Result.error("功能未实现");
    }

    /**
     * 用户购买代金券
     * @param couponId  优惠券ID
     */
    @PostMapping("/buy")
    public Result<Void> buyCoupon(Long couponId) {
        log.info("用户购买代金券，优惠券ID：{}", couponId);

        return Result.error("功能未实现");
    }
}
