package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.model.UserInfo;

public interface IUserService {

    public UserInfo selectUserInfoByUserName(String userName);

    public Integer createNormalUser(UserInfo userInfo);

    public UserLoginResponse login(UserLoginRequest loginRequest);
}
