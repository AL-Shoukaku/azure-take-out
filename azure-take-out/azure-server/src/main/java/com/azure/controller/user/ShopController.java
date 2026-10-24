package com.azure.controller.user;

import com.azure.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")  // 防止与管理端的 ShopController 冲突，指定 bean 名称
@RequestMapping("/user/shop")
@Tag(name = "用户端商铺管理")
@Slf4j
public class ShopController {

    public static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("/status")
    @Operation(summary = "用户端获取店铺的营业状态")
    public Result<Integer> getShopStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        if (status == null) {
            status = 0; // 默认状态为打烊中
        }
        log.info("用户端查询店铺的营业状态，状态为: {}", (status == 1 ? "营业中" : "打烊中"));
        return Result.success(status);
    }
}
