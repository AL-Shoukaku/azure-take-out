package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

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

    /**
     * 根据时间获取总用户数
     * @param endTime  截止时间
     * @return 总用户数
     */
    @Select("select count(*) from user where create_time <= #{endTime}")
    Integer getTotalUserByTime(LocalDateTime endTime);

    /**
     * 根据时间获取新增用户数
     * @param beginTime  开始时间
     * @param endTime    结束时间
     * @return 新增用户数
     */
    @Select("select count(*) from user where create_time >= #{beginTime} and create_time <= #{endTime}")
    Integer getNewUserByTime(LocalDateTime beginTime, LocalDateTime endTime);
}
