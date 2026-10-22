package com.azure.mapper;

import com.azure.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据 account 查询用户信息
     * @param account   用户账号
     * @return          用户信息
     */
    @Select("select * from user where account = #{account}")
    User getByAccount(String account);

    /**
     * 插入一个新用户
     * @param user  用户信息
     */
    void save(User user);

    /**
     * 根据 id 查询用户信息
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 按 ID 更新用户的非空字段
     * @param user 用户信息，必须包含用户 ID
     * @return 受影响行数
     */
    int updateUser(User user);

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

    /**
     * 根据 map 中的条件统计用户数量，包括起始和终止时间
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
