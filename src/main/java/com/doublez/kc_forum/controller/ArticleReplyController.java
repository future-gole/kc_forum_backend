package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.common.utiles.AuthUtils;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.ArticleReplyServiceImpl;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/articleReply")
public class ArticleReplyController {

    @Autowired
    private ArticleReplyServiceImpl articleReplyServiceImpl;

    @Autowired
    private UserServiceImpl userService;

    @Autowired//TODO 调service层好还是Mapper层好？
    private ArticleMapper articleMapper;

    @PostMapping("/createArticleReply")
    public Result createArticleReply(HttpServletRequest request, @RequestBody @Validated ArticleReplyAddRequest articleReplyAddRequest) {
        //被禁言
        Long userId = JwtUtil.getUserId(request);
        User user = userService.selectUserInfoById(userId);
        AuthUtils.userBannedChecker(user);

        articleReplyServiceImpl.createArticleReply(articleReplyAddRequest);

        return Result.sucess();
    }
}
