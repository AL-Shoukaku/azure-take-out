package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PasswordEditDTO implements Serializable {

    //当前登录员工id，可省略；传入时必须与JWT中的员工id一致
    private Long empId;

    //旧密码
    private String oldPassword;

    //新密码
    private String newPassword;

}
