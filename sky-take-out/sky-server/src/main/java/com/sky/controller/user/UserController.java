package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.PasswordEditDTO;
import com.sky.dto.UserInfoDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.vo.UserInfoVO;
import com.sky.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/user")
@Tag(name = "用户端用户管理")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result register(@RequestBody UserRegisterDTO userRegisterDTO) {
        log.info("用户注册, account: {}", userRegisterDTO.getAccount());
        userService.register(userRegisterDTO);
        return Result.success();
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("用户登录, account: {}", userLoginDTO.getAccount());
        UserLoginVO userLoginVO = userService.login(userLoginDTO);
        return Result.success(userLoginVO);
    }

    /**
     * 用户退出登录,由前端负责清楚 cookie 里的 jwt 令牌以及 Pinia 里的用户信息,后端不做任何处理
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "用户退出登录")
    public Result<Void> logout() {
        return Result.success();
    }

    @PutMapping("/update")
    @Operation(summary = "更新用户信息")
    public Result<Void> updateUserInfo(@RequestBody UserInfoDTO userInfoDTO) {
        log.info("更新用户信息, user: {}", userInfoDTO);
        userService.updateUserInfo(userInfoDTO);
        return Result.success();
    }

    @GetMapping("/info")
    @Operation(summary = "查询用户信息")
    public Result<UserInfoVO> queryUserInfo() {
        log.info("查询当前用户的信息, id: {}", BaseContext.getCurrentId());
        UserInfoVO userInfoVO = userService.getCurrentUserInfo();
        return Result.success(userInfoVO);
    }

    @PutMapping("/editPassword")
    @Operation(summary = "用户修改密码")
    public Result<Void> editPassword(@RequestBody PasswordEditDTO passwordEditDTO) {
        log.info("用户修改密码, id: {}", BaseContext.getCurrentId());
        userService.editPassword(passwordEditDTO);
        return Result.success();
    }
}
