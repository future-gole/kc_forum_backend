package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

public interface IUserService {

    public User selectUserInfoByUserName(String userName);

    public Integer createNormalUser(User user);

    public UserLoginResponse login(UserLoginRequest loginRequest);

    public User selectUserInfoById(Long id);

    /**
     * 更新用户发帖数目
     * @param id
     */
    public void updateOneArticleCountById(Long id,int increment);

    /**
     * 批量提取用户id
     * @param userIds
     * @return Map<Long, User>
     */
    Map<Long, User> selectUserInfoByIds(List<Long> userIds);
    @Transactional
    boolean   modifyUserInfoById(User user);
    @Transactional
    boolean modifyUserInfoPasswordById(String password, Long id);
}
