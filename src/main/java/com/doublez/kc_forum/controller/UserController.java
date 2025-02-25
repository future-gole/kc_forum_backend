package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.ModifyUerRequest;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173") // 指定前端地址
@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "用户类",description = "用户相关api")
public class UserController {

    @Autowired
    private UserServiceImpl userService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result register(@RequestBody @Validated
                        RegisterRequest registerRequest) {
        log.info("用户注册：{}", registerRequest.getUserName());
        //确认两次密码是否相等
        if(!registerRequest.getPassword().equals(registerRequest.getRepeatPassword())) {
            log.warn(ResultCode.FAILED_TWO_PWD_NOT_SAME.toString());
            return Result.failed(ResultCode.FAILED_TWO_PWD_NOT_SAME);
        }

        //!!!!不能注入，要创建新的对象
        User user = new User();
        //密码加密
        registerRequest.setPassword(SecurityUtil.encrypt(registerRequest.getPassword()));
        //类型转化
        try {
            BeanUtils.copyProperties(registerRequest, user);
        } catch (BeansException e) {
            log.error(ResultCode.ERROR_TYPE_CHANGE.toString());
            throw new ApplicationException(Result.failed(ResultCode.ERROR_TYPE_CHANGE));
        }

        userService.createNormalUser(user);
        //返回成功
        return Result.sucess();
    }

    @PostMapping("/login")
    @Operation(summary = "用户登陆")
    public UserLoginResponse login(@RequestBody @Validated
                                   UserLoginRequest userLoginRequest){
        log.info("用户登录{}", userLoginRequest.getUserName());
        return userService.login(userLoginRequest);
    }

    /**
     * 获取用户信息，传入id的时候为对应查询对应用户信息，不传入时从请求头中获取id
     * @param request
     * @param id
     * @return
     */
    @Operation(summary = "获取用户信息",description = "可以选择是否传入参数，未穿入参数代表查询当前用户信息，否则查询对应用户信息")
    @GetMapping("/info")
    public User getUserInfo(HttpServletRequest request, @RequestParam(value = "id", required = false) Long id) {
        //如果id为空，获取当前用户信息
        if (id == null) {
            Long userId = JwtUtil.getUserId(request);
            // 4. 根据用户 ID 查询用户信息
            return userService.selectUserInfoById(userId);
        } else {
            //查询对应用户id
            return userService.selectUserInfoById(id);
        }
    }
    @PostMapping("/modifyInfo")
    @Operation(summary = "修改用户基本信息",description = "此方法可以修改的信息只有ModifyUerRequest类里面的信息")
    public boolean  modifyInfo(HttpServletRequest request,@RequestBody @Validated ModifyUerRequest modifyUerRequest) {
        //获取当前用户id
        Long userId = JwtUtil.getUserId(request);
        User user = new User();
        //类型转化
        try {
            BeanUtils.copyProperties(modifyUerRequest, user);
            user.setId(userId);
        } catch (BeansException e) {
            log.error(ResultCode.ERROR_TYPE_CHANGE.toString());
            throw new ApplicationException(Result.failed(ResultCode.ERROR_TYPE_CHANGE));
        }
        return userService.modifyUserInfoById(user);
    }

    @PostMapping("/modifyPassword")
    @Operation(summary = "修改用户密码")
    public Result modifyInfoPassword(HttpServletRequest request, @NotBlank String password,@NotBlank String repeatPassword) {
        //确认两次密码是否相等
        if(!password.equals(repeatPassword)) {
            log.warn(ResultCode.FAILED_TWO_PWD_NOT_SAME.toString());
            return Result.failed(ResultCode.FAILED_TWO_PWD_NOT_SAME);
        }
        //获取当前用户id
        Long userId = JwtUtil.getUserId(request);
        //密码加密
        password = SecurityUtil.encrypt(password);
        userService.modifyUserInfoPasswordById(password,userId);
            return Result.sucess();
    }
}
