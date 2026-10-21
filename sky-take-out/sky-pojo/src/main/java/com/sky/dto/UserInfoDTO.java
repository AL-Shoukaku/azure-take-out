package com.sky.dto;

import lombok.Data;

/**
 * 用于传递用户的信息
 */
@Data
public class UserInfoDTO {
    private Long id;

    private String username;

    private String name;

    private String phone;

    private String sex;

    private String idNumber;

    private String avatar;
}
