package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleReplyMetaCacheDTO;
import com.doublez.kc_forum.common.pojo.response.ViewArticleReplyResponse;
import com.doublez.kc_forum.common.utiles.AuthUtils;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.ArticleReplyServiceImpl;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/articleReply")
@Tag(name = "帖子回复类",description = "帖子回复相关api")
public class ArticleReplyController {

    @Autowired
    private ArticleReplyServiceImpl articleReplyServiceImpl;

    @Autowired
    private UserServiceImpl userService;

    @Autowired//TODO 调service层好还是Mapper层好？
    private ArticleMapper articleMapper;

    @PostMapping("/createArticleReply")
    @Operation(summary = "创建回复帖子",
            description = "先通过传入HttpServletRequest，后端通过获取token来获取当前用户id，进行判断是否被禁言，然后通过ArticleReplyAddRequest传入的参数进行创建帖子")
    public Result createArticleReply(HttpServletRequest request, @RequestBody @Validated ArticleReplyAddRequest articleReplyAddRequest) {
        //被禁言
        Long userId = JwtUtil.getUserId(request);
        User user = userService.selectUserInfoById(userId);
        AuthUtils.userBannedChecker(user);
        articleReplyAddRequest.setReplyUserId(userId);

        articleReplyServiceImpl.createArticleReply(articleReplyAddRequest);

        return Result.success();
    }
    @GetMapping("/getArticleReplies")
    @Operation(summary = "获取回复帖子",
            description = "通过articleId来分页获取当前列表下帖子")
    public ViewArticleReplyResponse getArticleReply(
            @NotNull @Parameter(name = "帖子Id") Long articleId,
            @NotNull Integer currentPage,@NotNull Integer pageSize) {
        //有效性校验
        if(articleId != null && articleId > 0) {
            return articleReplyServiceImpl.getArticleReply(articleId,currentPage,pageSize);
        }
        log.warn("传入参数有错，articleId:{}", articleId);
        throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
    }

    @PostMapping("/deleteArticleReply")
    @Operation(summary = "删除回复帖子")
    public void deleteArticleReply(HttpServletRequest request,
                                   @NotNull @Parameter(name = "回复帖子id") Long articleReplyId,
                                   @NotNull @Parameter(name = "帖子id") Long articleId) {
        if(articleReplyId > 0) {
            Long userId = JwtUtil.getUserId(request);
            if(articleReplyServiceImpl.deleteArticleReply(userId,articleReplyId,articleId) != 1){
                log.error("帖子删除异常：{}", articleReplyId);
                throw new BusinessException(ResultCode.FAILED_REPLY_DELETE);
            }
        }else throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
    }
    @GetMapping("/getChildrenReplies")
    @Operation(summary = "获取子回复帖子",
            description = "通过replyId来分页获取当前回复下子回复")
    public List<ArticleReplyMetaCacheDTO> getChildrenReplyByReplyId(
            @NotNull @Parameter(name = "父回复贴Id") Long replyId,
            @NotNull Integer currentPage,@NotNull Integer pageSize){
        if(replyId <= 0){
            log.warn("传入参数有错，replyId:{}", replyId);
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        return articleReplyServiceImpl.getChildrenReplyByReplyId(replyId,currentPage,pageSize);
    }
}
