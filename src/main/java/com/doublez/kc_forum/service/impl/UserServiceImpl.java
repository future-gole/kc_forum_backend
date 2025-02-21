package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.mapper.UserMapper;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public User selectUserInfoByUserName(String userName) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserName, userName));

        if (user == null) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user;
    }

    @Transactional
    @Override
    public Integer createNormalUser(User user) {
        //非空校验
        if (user == null || user.getUserName() == null
                || user.getPassword() == null || user.getNickName() == null) {
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
            result = userMapper.insert(user);


        if(result != 1) {
            //打印日志
            log.info(ResultCode.FAILED_CREATE.toString());
            //抛异常
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CREATE));
        }
        //打印日志
        log.info("新增用户成功,username:" + user.getUserName());
        return result;
    }

    @Override
    public UserLoginResponse login(UserLoginRequest loginRequest) {
        //判断用户是否存在,查询信息
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserName, loginRequest.getUserName())
                .eq(User::getDeleteState,0));
        //用户不存在
        if(user == null){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        //用户密码错误
        if(!SecurityUtil.checkPassword(loginRequest.getPassword(), user.getPassword())){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_LOGIN));
        }
        //密码正确
        UserLoginResponse loginResponse = new UserLoginResponse();
        loginResponse.setUserId(user.getId());
        //放入载荷
        Map<String,Object> map = new HashMap<>();
        map.put("userName",loginRequest.getUserName());
        map.put("id", user.getId());
        loginResponse.setAuthorization(JwtUtil.getToken(map));
        return loginResponse;

    }

    @Override
    public User selectUserInfoById(Long id) {
        //判断用户是否存在
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, id).eq(User::getDeleteState,0));
        //不存在
        if (user == null) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        log.info("查询用户成功");
        return user;
    }

    /**
     * 根据id修改该用户文章数量
     * @param id
     */
    @Override
    public void updateOneArticleCountById(Long id,int increment) {
        if(id == null || id <= 0 ) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 直接更新，利用数据库原子操作避免并发问题
        int rows = userMapper.update( new LambdaUpdateWrapper<User>()
                .setSql("article_count = article_count + " + increment) // 确保字段名与数据库一致
                .eq(User::getId, id)
                .eq(User::getState,0)//判断是否被禁言
                .eq(User::getDeleteState, 0)
        );
        if (rows != 1) {
            // 可能原因：用户不存在、已删除或计数未变化
            log.warn("更新用户发帖数量失败, userId: {}", id);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        log.info("用户：文章数量更新");
    }

    /**
     * 批量提取用户id
     * @param userIds
     * @return Map<Long, User>
     */
    public Map<Long, User> selectUserInfoByIds(List<Long> userIds) {
        // 3. 批量查询 User 信息
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

}
