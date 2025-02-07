package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.mapper.UserInfoMapper;
import com.doublez.kc_forum.model.UserInfo;
import com.doublez.kc_forum.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public UserInfo selectUserInfoByUserName(String userName) {
        UserInfo userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUserName, userName));

        if (userInfo == null) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return userInfo;
    }

    @Override
    public Integer createNormalUser(UserInfo userInfo) {
        //非空校验
        if (userInfo == null || userInfo.getUserName() == null
                || userInfo.getPassword() == null || userInfo.getNickName() == null) {
            //打印日志
            log.warn(ResultCode.FAILED_USER_NOT_EXISTS.toString());
            //抛异常
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        //判断用户名是否存在,名字有重名的可能，可以转为邮箱
        //TODO 判断邮箱是否存在即可

        //新增用户默认值用代码控制
        //TODO。。。
        int result = 0;
        try {
            result = userInfoMapper.insert(userInfo);
        } catch (Exception e) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CREATE));
        }

        if(result != 1) {
            //打印日志
            log.info(ResultCode.FAILED_CREATE.toString());
            //抛异常
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CREATE));
        }
        //打印日志
        log.info("新增用户成功,username:" + userInfo.getUserName());
        return result;
    }

    @Override
    public UserLoginResponse login(UserLoginRequest loginRequest) {
        //判断用户是否存在,查询信息
        UserInfo userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getUserName, loginRequest.getUserName())
                .eq(UserInfo::getDeleteState,0));
        //用户不存在
        if(userInfo == null){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        //用户密码错误
        if(!SecurityUtil.checkPassword(loginRequest.getPassword(), userInfo.getPassword())){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_LOGIN));
        }
        //密码正确
        UserLoginResponse loginResponse = new UserLoginResponse();
        loginResponse.setUserId(userInfo.getId());
        //放入载荷
        Map<String,Object> map = new HashMap<>();
        map.put("userName",loginRequest.getUserName());
        map.put("id",userInfo.getId());
        loginResponse.setToken(JwtUtil.getToken(map));
        return loginResponse;

    }


}
