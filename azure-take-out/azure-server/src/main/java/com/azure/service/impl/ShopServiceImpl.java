package com.azure.service.impl;

import com.azure.cache.CacheClient;
import com.azure.cache.RedisConstants;
import com.azure.mapper.ShopStatusMapper;
import com.azure.service.ShopService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;

@Service
public class ShopServiceImpl implements ShopService {

    @Autowired
    private ShopStatusMapper shopStatusMapper;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Integer getShopStatus() {
        return cacheClient.getOrLoad(RedisConstants.SHOP_STATUS_KEY, new TypeReference<Integer>() {},
                () -> shopStatusMapper.getShopStatus(), RedisConstants.CACHE_SHOP_STATUS_TTL);
    }

    @Override
    public void updateShopStatus(int status) {
        shopStatusMapper.updateShopStatus(status);
        cacheClient.set(RedisConstants.SHOP_STATUS_KEY, status, RedisConstants.CACHE_SHOP_STATUS_TTL);
    }
}
