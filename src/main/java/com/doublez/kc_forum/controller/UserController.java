package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.request.ModifyUerRequest;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "用户类",description = "用户相关api")
public class UserController {

    @Autowired
    private UserServiceImpl userService;

    @PostMapping("/uploadAvatar")
    @Operation(summary = "上传用户头像", description = "上传用户头像并更新用户信息的avatarUrl")
    public Result<String> uploadAvatar(HttpServletRequest request, @Validated @RequestParam("file") MultipartFile file) {
        // 获取当前用户id
        Long userId = JwtUtil.getUserId(request);
        log.info("用户 {} 上传头像", userId);

        try {
            // 调用服务上传头像
            String avatarUrl = userService.uploadAvatar(userId, file);
            return Result.success(avatarUrl); // 返回头像URL
        } catch (Exception e) {
            log.error("用户 {} 上传头像失败", userId, e);
            throw new BusinessException(ResultCode.UPLOAD_AVATAR_FAILED); // 返回上传失败
        }
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result register(@RequestBody @Validated
                        RegisterRequest registerRequest) {
        log.info("用户开始注册：{}", registerRequest.getUserName());
        //确认两次密码是否相等
        if(!registerRequest.getPassword().equals(registerRequest.getRepeatPassword())) {
            log.warn(ResultCode.FAILED_TWO_PWD_NOT_SAME.toString());
            throw new BusinessException(ResultCode.FAILED_TWO_PWD_NOT_SAME);
        }
        //返回成功
        return userService.createNormalUser(registerRequest);
    }

    @RequestMapping("/login")
    @Operation(summary = "用户登陆")
    public UserLoginResponse login(@RequestBody @Validated
                                   UserLoginRequest userLoginRequest,
                                   HttpServletResponse response){
        log.info("用户登录,邮箱：{}", userLoginRequest.getEmail());
        return userService.login(userLoginRequest,response);
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
            if(id < 0){
                throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE,"用户id小于0");
            }
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
            throw new SystemException(ResultCode.ERROR_TYPE_CHANGE);
        }
        return userService.modifyUserInfoById(user);
    }

    @PostMapping("/modifyPassword")
    @Operation(summary = "修改用户密码")
    public Result modifyInfoPassword(HttpServletRequest request, @Parameter(description = "密码") @NotBlank String password,
                                     @Parameter(description = "重复密码")@NotBlank String repeatPassword) {
        //确认两次密码是否相等
        if(!password.equals(repeatPassword)) {
            log.warn(ResultCode.FAILED_TWO_PWD_NOT_SAME.toString());
            throw new BusinessException(ResultCode.FAILED_TWO_PWD_NOT_SAME);
        }
        //获取当前用户id
        Long userId = JwtUtil.getUserId(request);

        userService.modifyUserInfoPasswordById(password,userId);
        return Result.success();
    }

    @PostMapping("/modifyEmail")
    @Operation(summary = "修改用户邮箱")
    public Result<?> modifyInfoEmail(HttpServletRequest request,@NotBlank @Email String email,@NotBlank String code) {

        //获取当前用户id
        Long userId = JwtUtil.getUserId(request);

        userService.modifyUserInfoEmailById(email,userId,code);
        return Result.success();
    }
}
