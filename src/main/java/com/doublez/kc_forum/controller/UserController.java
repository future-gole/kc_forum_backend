package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
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
public class UserController {

    @Autowired
    private UserServiceImpl userService;

    @PostMapping("/register")
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
            log.warn(ResultCode.ERROR_TYPE_CHANGE.toString());
        }

        userService.createNormalUser(user);
        //返回成功
        return Result.sucess();
    }

    @PostMapping("/login")
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
}
