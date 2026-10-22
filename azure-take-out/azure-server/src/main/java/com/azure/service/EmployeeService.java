package com.azure.service;

import com.azure.dto.EmployeeDTO;
import com.azure.dto.EmployeeLoginDTO;
import com.azure.dto.EmployeePageQueryDTO;
import com.azure.dto.PasswordEditDTO;
import com.azure.entity.Employee;
import com.azure.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 新增员工
     * @param employeeDTO
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用/禁用指定 id 的员工
     * @param status
     * @param id
     */
    void startOrStop(Integer status, long id);

    /**
     * 根据 id 查询指定员工
     * @param id
     * @return
     */
    Employee getById(Long id);

    /**
     * 校验旧密码后修改当前登录员工的密码
     */
    void editPassword(PasswordEditDTO passwordEditDTO);

    /**
     * 更新员工的信息
     * @param employeeDTO
     */
    void update(EmployeeDTO employeeDTO);
}
