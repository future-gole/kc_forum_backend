package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.ArticleAddRequest;
import com.doublez.kc_forum.common.pojo.request.UpdateArticleRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleDetailResponse;
import com.doublez.kc_forum.common.pojo.response.ViewArticlesResponse;
import com.doublez.kc_forum.common.utiles.AuthUtils;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.ArticleServiceImpl;
import com.doublez.kc_forum.service.impl.BoardServiceImpl;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/article")
@Tag(name = "帖子类",description = "帖子相关api")
@Slf4j
public class ArticleController {

    @Autowired
    private ArticleServiceImpl articleService;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private BoardServiceImpl boardService;


    @PostMapping("/create")
    @Operation(summary = "创建帖子", description = "创建一个新的帖子")
    @ApiResponse(responseCode = "200", description = "成功")
    public Result<Object> create(HttpServletRequest request, @RequestBody @Validated ArticleAddRequest articleAddRequest) {
        //需要判断用户是否被禁言
        Long userId = JwtUtil.getUserId(request);
        User user = userService.selectUserInfoById(userId);
        AuthUtils.userBannedChecker(user);;

        //需要判断板块是否正常
        Board board = boardService.selectOneBoardById(articleAddRequest.getBoardId());
        //板块不存在或者板块被禁言
        if(board == null || board.getState() == 1 ||board.getDeleteState() == 1){
            log.warn("板块不存在或者已经被禁言");
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        //创建新的对象而不是注入！！！,要不然会导致插入一次之后id就不会变了
        Article article = new Article();
        //类型转化
        BeanUtils.copyProperties(articleAddRequest,article);
        //添加用户id
        article.setUserId(userId);
        articleService.createArticle(article);

        return Result.sucess();

    }

    /**
     * 获取板块下的所有帖子
     * @param boardId
     * @return
     */
    @Operation(summary = "获取板块下的所有帖子", description = "根据板块 ID 获取所有帖子")
    @GetMapping("/getAllArticlesByBoardId")
    public List<ViewArticlesResponse> getAllArticlesByBoardId(@Parameter(description = "板块 ID") @RequestParam(required = false) Long boardId) {
        if(boardId == null){
            return articleService.getAllArticlesByBoardId(null);
        }else if(boardId < 0){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }

        return articleService.getAllArticlesByBoardId(boardId);
    }

    /**
     * 根据帖子id查询帖子详情
     * @param articleId 文章id
     * @return ArticleDetailResponse
     */
    @Operation(summary = "获取帖子详情", description = "根据帖子 ID 获取所有帖子详情")
    @GetMapping("/getArticleDetailById")
    public ArticleDetailResponse getArticleDetailById(HttpServletRequest request, @Parameter(description = "帖子ID") Long articleId) {
        if(articleId != null && articleId > 0){
            Long userId = JwtUtil.getUserId(request);
            return articleService.getArticleDetailById(userId,articleId);
        }
        log.error("传入参数有错，articleId:{}", articleId);
        throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
    }

    @GetMapping("/getAllArticlesByUserId")
    @Operation(summary = "获取用户所属帖子", description = "根据用户 ID 获取其下所有帖子列表，由前端控制传入的是当前用户还是查询的目标用户")
    public List<ViewArticlesResponse> getAllArticlesByUserId(@Parameter(description = "用户ID")Long userId) {
        if(userId == null || userId < 0){
            log.error("参数校验失败 userId:{}", userId);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return articleService.getAllArticlesByUserId(userId);
    }

    /**
     * 更新帖子
     * @param request 获取当前用户id
     * @param updateArticleRequest 获取需要更新的数据
     * @return 成功返回true
     */
    @PostMapping("/updateArticle")
    @Operation(summary = "根据帖子id，更新帖子",
            description = "通过request，获取当前用户id，进行鉴权和是否被禁言等判断，由前端传入updateArticleRequest对象，更新对应帖子信息")
    public boolean updateArticle(HttpServletRequest request, @RequestBody @Validated UpdateArticleRequest updateArticleRequest) {
        //简单判断
        if(updateArticleRequest.getId() < 1){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        //鉴权
        Long userId = JwtUtil.getUserId(request);
        AuthUtils.userPermissionChecker(userId,updateArticleRequest.getId(),articleService::getUserId);
        //被禁言
        User user = userService.selectUserInfoById(userId);
        AuthUtils.userBannedChecker(user);
        //查询
        return articleService.updateArticle(updateArticleRequest);

    }

    @PostMapping("/deleteArticle")
    @Operation(summary = "根据帖子id，删除对应帖子")
    public boolean deleteArticle(HttpServletRequest request, @Parameter(description = "帖子ID")@NotNull Long articleId) {
        if(articleId == null || articleId <= 0){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Long userId = JwtUtil.getUserId(request);
        //鉴权
        AuthUtils.userPermissionChecker(userId,articleId,articleService::getUserId);

        return articleService.deleteArticle(articleId);
    }

}
