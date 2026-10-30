package com.azure.service;

public interface ShopService {

    /**
     * 更新店铺状态
     * @return    店铺状态，0表示关闭，1表示开启
     */
    Integer getShopStatus();

    /**
     * 更新店铺状态
     * @param status    店铺状态，0表示关闭，1表示开启
     */
    void updateShopStatus(int status);
}
