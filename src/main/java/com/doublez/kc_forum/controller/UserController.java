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
import com.doublez.kc_forum.service.impl.RefreshTokenService;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import io.jsonwebtoken.Claims;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;


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
            return Result.sucess(avatarUrl); // 返回头像URL
        } catch (Exception e) {
            log.error("用户 {} 上传头像失败", userId, e);
            return Result.failed(ResultCode.UPLOAD_FAILED); // 返回上传失败
        }
    }

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

    // 令牌续期接口
//    @PostMapping("/renew")
//    public ResponseEntity<?> renewToken(@RequestHeader("Authorization") String authHeader) {
//        // 从Authorization头获取令牌
//        String token = null;
//        if (authHeader != null && authHeader.startsWith("Bearer ")) {
//            token = authHeader.substring(7);
//        }
//
//        if (token == null) {
//            log.info(ResultCode.FAILED_TOKEN_EXISTS.toString());
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.failed(ResultCode.FAILED_TOKEN_EXISTS));
//        }
//
//        // 从令牌中获取用户信息
//        Claims claims = JwtUtil.parseToken(token);
//
//        Long userId = null;
//        String email = null;
//        if (claims != null && claims.containsKey("Id")) {
//            try {
//                userId = Long.valueOf(claims.get("Id").toString());
//                email = String.valueOf(claims.get("email").toString());
//            }catch (Exception e) {
//                throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
//            }
//        }
//        //验证用户是否存在
//        User user = null;
//        if(userId != null) {
//            user = userService.selectUserInfoById(userId);
//        }
//        if (user == null) {
//            log.info(ResultCode.FAILED_USER_NOT_EXISTS.toString());
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
//        }
//
//        //放入载荷
//        Map<String,Object> map = new HashMap<>();
//        map.put("email",email);
//        map.put("Id", userId);
//        String newToken = JwtUtil.getToken(map);
//
//        // 返回新令牌
//        Map<String, String> response = new HashMap<>();
//        response.put("token", newToken);
//
//        return ResponseEntity.ok(response);
//    }

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
    public Result modifyInfoPassword(HttpServletRequest request, @Parameter(description = "密码") @NotBlank String password,
                                     @Parameter(description = "重复密码")@NotBlank String repeatPassword) {
        //确认两次密码是否相等
        if(!password.equals(repeatPassword)) {
            log.warn(ResultCode.FAILED_TWO_PWD_NOT_SAME.toString());
            return Result.failed(ResultCode.FAILED_TWO_PWD_NOT_SAME);
        }
        //获取当前用户id
        Long userId = JwtUtil.getUserId(request);

        userService.modifyUserInfoPasswordById(password,userId);
        return Result.sucess();
    }

    @PostMapping("/modifyEmail")
    @Operation(summary = "修改用户邮箱")
    public Result<?> modifyInfoPassword(HttpServletRequest request,@NotBlank @Email String email) {

        //获取当前用户id
        Long userId = JwtUtil.getUserId(request);

        userService.modifyUserInfoEmailById(email,userId);
        return Result.sucess();
    }
}
