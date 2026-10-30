package com.azure.controller.admin;

import com.azure.result.Result;
import com.azure.service.ShopService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")  // 防止与用户端的 ShopController 冲突，指定 bean 名称
@RequestMapping("/admin/shop")
@Tag(name = "管理端商铺管理")
@Slf4j
public class ShopController {

    @Autowired
    private ShopService shopService;

    @Operation(summary = "设置店铺的营业状态")
    @PutMapping("/{status}")
    public Result openOrClose(@PathVariable Integer status) {
        log.info("设置店铺的营业状态, shopId: {}", (status == 1 ? "营业中" : "打烊中"));
        shopService.updateShopStatus(status);
        return Result.success();
    }

    @GetMapping("/status")
    @Operation(summary = "获取店铺的营业状态")
    public Result<Integer> getShopStatus() {
        Integer status = shopService.getShopStatus();
        log.info("管理端查询店铺的营业状态，状态为: {}", (status == 1 ? "营业中" : "打烊中"));
        return Result.success(status);
    }
}
