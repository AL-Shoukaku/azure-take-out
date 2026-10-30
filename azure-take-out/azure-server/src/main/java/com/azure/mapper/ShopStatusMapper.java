package com.azure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopStatusMapper {
    @Update("UPDATE shop_status SET status = #{status} WHERE id = 1")
    void updateShopStatus(int status);

    @Select("SELECT status FROM shop_status WHERE id = 1")
    int getShopStatus();
}
