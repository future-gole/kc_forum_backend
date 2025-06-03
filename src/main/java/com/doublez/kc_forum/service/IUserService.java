package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.model.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IUserService {


    String uploadAvatar(Long userId, MultipartFile file);
    User selectUserInfoByUserEmail(String email);

    Result createNormalUser(RegisterRequest registerRequest);

    UserLoginResponse login(UserLoginRequest loginRequest, HttpServletResponse response);

    User selectUserInfoById(Long id);

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

    boolean   modifyUserInfoById(User user);

    void modifyUserInfoPasswordById(String password, Long id);

    Result<?> modifyUserInfoEmailById(String email,Long id,String code);
}
