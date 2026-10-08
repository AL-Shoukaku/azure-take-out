package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")  // 防止与管理端的 ShopController 冲突，指定 bean 名称
@RequestMapping("/user/shop")
@Api("用户端商铺管理")
@Slf4j
public class ShopController {

    public static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("/status")
    @ApiOperation("用户端获取店铺的营业状态")
    public Result<Integer> getShopStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        if (status == null) {
            status = 0; // 默认状态为打烊中
        }
        log.info("用户端查询店铺的营业状态，状态为: {}", (status == 1 ? "营业中" : "打烊中"));
        return Result.success(status);
    }
}
