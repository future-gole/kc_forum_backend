package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.model.UserInfo;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173") // 指定前端地址
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private UserInfo userInfo;

    @PostMapping("/register")
    public Result register(@RequestBody @Validated
                        RegisterRequest registerRequest) {
        log.info("用户注册：{}", registerRequest.getUserName());
        //确认两次密码是否相等
        if(!registerRequest.getPassword().equals(registerRequest.getRepeatPassword())) {
            log.warn(ResultCode.FAILED_TWO_PWD_NOT_SAME.toString());
            return Result.failed(ResultCode.FAILED_TWO_PWD_NOT_SAME);
        }

        //密码加密
        registerRequest.setPassword(SecurityUtil.encrypt(registerRequest.getPassword()));
        //类型转化
        BeanUtils.copyProperties(registerRequest, userInfo );

        userService.createNormalUser(userInfo);
        //返回成功
        return Result.sucess();
    }

    @PostMapping("/login")
    public UserLoginResponse login(@RequestBody @Validated
                                   UserLoginRequest userLoginRequest){
        log.info("用户登录{}", userLoginRequest.getUserName());
        return userService.login(userLoginRequest);
    }
}
