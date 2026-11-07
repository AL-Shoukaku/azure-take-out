package com.azure.controller.user;

import com.azure.dto.coupon.UserCouponPageQueryDTO;
import com.azure.result.PageResult;
import com.azure.result.Result;
import com.azure.service.UserCouponService;
import com.azure.vo.coupon.UserCouponVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userUserCouponController")
@RequestMapping("/user/userCoupon")
@Slf4j
@Tag(name = "C端-我的优惠券相关接口")
public class UserCouponController {

    @Autowired
    private UserCouponService userCouponService;

    @GetMapping("/page")
    @Operation(summary = "用户分页查询自己持有的优惠券")
    public Result<PageResult> page(UserCouponPageQueryDTO userCouponPageQueryDTO) {
        log.info("用户查询自己持有的优惠券: {}", userCouponPageQueryDTO);
        PageResult pageResult = userCouponService.pageQuery(userCouponPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/details/{id}")
    @Operation(summary = "用户查询持有的优惠券详情")
    public Result<UserCouponVO> details(@PathVariable Long id) {
        log.info("用户查询持有的优惠券详情，优惠券id：{}", id);
        UserCouponVO userCouponVO = userCouponService.queryUserCouponDetail(id);
        return Result.success(userCouponVO);
    }
}
