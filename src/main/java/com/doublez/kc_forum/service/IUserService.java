package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.model.User;

public interface IUserService {

    public User selectUserInfoByUserName(String userName);

    public Integer createNormalUser(User user);

    public UserLoginResponse login(UserLoginRequest loginRequest);

    public User selectUserInfoById(Long id);
}
