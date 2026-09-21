package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据 openid 查询用户信息
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getUserByOpenid(String openid);

    /**
     * 插入一个新用户
     * @param user
     */
    void save(User user);

    /**
     * 根据 id 查询用户信息
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);
}
