package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.ArticleAddRequest;
import com.doublez.kc_forum.common.pojo.request.UpdateArticleRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleDetailResponse;
import com.doublez.kc_forum.common.pojo.response.ViewArticlesResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.ArticleServiceImpl;
import com.doublez.kc_forum.service.impl.BoardServiceImpl;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Autowired
    private ArticleServiceImpl articleService;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private BoardServiceImpl boardService;


    @PostMapping("/create")
    public Result<Object> create(HttpServletRequest request, @RequestBody @Validated ArticleAddRequest articleAddRequest) {
        //需要判断用户是否被禁言
        Long userId = JwtUtil.getUserId(request);
        User user = userService.selectUserInfoById(userId);
        //被禁用
        if(user.getState() == 1){
            log.warn("该用户已被禁言，id：{}",userId);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_BANNED));
        }
        //需要判断板块是否正常
        Board board = boardService.selectOneBoardById(articleAddRequest.getBoardId());
        //板块不存在或者板块被禁言
        if(board == null || board.getState() == 1 ||board.getDeleteState() == 1){
            log.warn("板块不存在或者已经被禁言");
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        //创建新的对象而不是注入！！！
        Article article = new Article();
        //类型转化
        BeanUtils.copyProperties(articleAddRequest,article);
        //添加用户id
        article.setUserId(userId);
        articleService.createArtical(article);

        return Result.sucess();

    }

    /**
     * 获取板块下的所有帖子
     * @param boardId
     * @return
     */
    @GetMapping("/getAllArticlesByBoardId")
    public List<ViewArticlesResponse> getAllArticlesByBoardId(@RequestParam(required = false) Long boardId) {
        if(boardId == null){
            return articleService.getAllArticlesByBoardId(null);
        }else if(boardId < 0){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        List<ViewArticlesResponse> viewArticlesResponses = articleService.getAllArticlesByBoardId(boardId);

        return viewArticlesResponses;
    }

    /**
     * 根据帖子id查询1帖子详情
     * @param articleId 文章id
     * @return ArticleDetailResponse
     */
    @GetMapping("/getArticleDetailById")
    public ArticleDetailResponse getArticleDetailById(HttpServletRequest request, Long articleId) {
        if(articleId != null && articleId > 0){
            Long userId = JwtUtil.getUserId(request);
            return articleService.getArticleDetailById(userId,articleId);
        }
        throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
    }

    @PostMapping("/updateArticle")
    public boolean UpdateArticle(HttpServletRequest request, @RequestBody @Validated UpdateArticleRequest updateArticleRequest) {
        //简单判断
        if(updateArticleRequest.getId() < 1){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        //鉴权
        Long userId = JwtUtil.getUserId(request);
        //无权限
        if(!userId.equals(articleService.getUserId(updateArticleRequest.getId()))){
            log.warn(ResultCode.FAILED_UNAUTHORIZED.toString()+"id: {}",userId);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_UNAUTHORIZED));
        }
        //被禁言
        User user = userService.selectUserInfoById(userId);
        if(user.getState() == 1 || user.getDeleteState() == 1){
            log.warn(ResultCode.FAILED_USER_BANNED.toString());
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_BANNED));
        }
        return articleService.updateArticle(updateArticleRequest);

    }
}
