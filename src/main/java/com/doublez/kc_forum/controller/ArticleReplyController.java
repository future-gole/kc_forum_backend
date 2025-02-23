package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.common.pojo.response.ViewArticleReplyResponse;
import com.doublez.kc_forum.common.pojo.response.ViewArticlesResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @GetMapping("/getArticleReplies")
    public List<ViewArticleReplyResponse> getArticleReply(HttpServletRequest request, Long articleId) {
        //有效性校验
        if(articleId != null && articleId > 0) {
            return articleReplyServiceImpl.getArticleReply(articleId);
        }
        log.error("传入参数有错，articleId:{}", articleId);
        throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
    }
}
