package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.exception.PasswordEditFailedException;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.security.PasswordHasher;
import com.sky.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        String storedHash = employee.getPassword();
        // 先检查密码是否正确，matches 方法能自动区分 MD5 和 Argon2id
        if (!passwordHasher.matches(password, storedHash)) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (!StatusConstant.ENABLE.equals(employee.getStatus())) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        // 如果密码正确，但数据库中存储的哈希是旧的 MD5，则升级为 Argon2id
        if (passwordHasher.needsUpgrade(storedHash)) {
            employee.setPassword(passwordHasher.encode(password));
            // update 会自动填充，登录时 baseContext 里没有当前用户 ID，需要手动设置，避免切面填充错误。
            BaseContext.setCurrentId(employee.getId());
            try {
                employeeMapper.update(employee);
            } finally {
                BaseContext.removeCurrentId();
            }
        }
        //3、返回实体对象
        return employee;
    }

    /**
     * 新增员工
     * @param employeeDTO
     */
    public void save(EmployeeDTO employeeDTO) {
        // 只有管理员可以新增员工
        Employee employee = employeeMapper.getById(BaseContext.getCurrentId());
        if (employee == null || !employee.getUsername().equals("admin")) {
            throw new AccountLockedException(MessageConstant.ONLY_ADMIN_CAN_OPERATE);
        }
        employee = new Employee();
        // 使用对象属性拷贝，属性名需要一致
        BeanUtils.copyProperties(employeeDTO, employee);
        // 设置 DTO 中没有的属性
        // 状态默认启用
        employee.setStatus(StatusConstant.ENABLE);
        // 密码默认123456，使用 Argon2id 哈希算法加密，避免明文存储
        employee.setPassword(passwordHasher.encode(PasswordConstant.DEFAULT_PASSWORD));
        // 创建人，修改人，创建时间，修改时间在切面中自动填充

        // 调用持久层，存入数据库
        employeeMapper.insert(employee);
    }

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        PageResult pageResult = new PageResult();
        // 使用 mybatis 里的 pageHelper 进行分页查询，基于threadLocal
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        // 要求使用 Page 对象进行返回
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);
        // 将密码掩码，防止泄露
        page.forEach(employee -> employee.setPassword("****"));
        pageResult.setTotal(page.getTotal());
        pageResult.setRecords(page.getResult());
        return  pageResult;
    }

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, long id) {
        Employee employee = employeeMapper.getById(BaseContext.getCurrentId());
        if (employee == null || !employee.getUsername().equals("admin")) {
            throw new AccountLockedException(MessageConstant.ONLY_ADMIN_CAN_OPERATE);
        }
        employee.setStatus(status);
        employee.setId(id);
        employeeMapper.update(employee);
    }

    /**
     * 根据 id 查询员工
     * @param id
     * @return
     */
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        employee.setPassword("****");   // 不让看密码
        return employee;
    }

    @Override
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        Long empId = BaseContext.getCurrentId();
        if (empId == null) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
        // 前端只传新旧密码；兼容文档中的 empId，但不能修改其他员工的密码。
        if (passwordEditDTO == null
                || !StringUtils.hasText(passwordEditDTO.getOldPassword())
                || !StringUtils.hasText(passwordEditDTO.getNewPassword())) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED);
        }
        if (!passwordEditDTO.getNewPassword().matches("[0-9A-Za-z]{6,20}")) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_FORMAT_ERROR);
        }

        // 读取数据库中的摘要，不能使用会掩码密码的员工详情服务。
        Employee current = employeeMapper.getById(empId);
        if (current == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (!StatusConstant.ENABLE.equals(current.getStatus())) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        String oldPassword = passwordEditDTO.getOldPassword();
        String storedHash = current.getPassword();
        if (!passwordHasher.matches(oldPassword, storedHash)) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        Employee update = Employee.builder().id(empId)
                .password(passwordHasher.encode(passwordEditDTO.getNewPassword())).build();
        // 只传 ID 和新哈希，复用通用更新；操作人和时间由自动填充处理。
        if (employeeMapper.update(update) == 0) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED);
        }
    }

    /**
     * 更新员工信息
     * @param employeeDTO
     */
    public void  update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);    // 拷贝属性
        // 修改时间和修改人会在切面中自动填充
        employeeMapper.update(employee);
    }
}
