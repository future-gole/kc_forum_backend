package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.utiles.AuthUtils;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.service.impl.LikesServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
@RestController
@RequestMapping("/likes")
@Tag(name = "点赞类",description = "点赞相关api")
public class LikesController {

    @Autowired
    private LikesServiceImpl likeService;

    @PostMapping("/addLike")
    @Operation(summary = "点赞",
            description = "根据HttpServletRequest获取当前用户id,并且根据传入的targetId和targetType来判断是给帖子点赞还是帖子回复表点赞")
    public void likeArticle(HttpServletRequest request,
                            @Parameter(name = "id",description = "可以是帖子id，也可以是回复贴id") @RequestParam @NotNull Long targetId,
                            @Parameter(name = "类型",description = "reply对应回复贴，article对应帖子")@RequestParam @NotNull String targetType) {
        //获取真实用户id
        Long userId = JwtUtil.getUserId(request);
        log.info("新增点赞：userId = {}, articleId = {}, targetType = {}", userId, targetId,targetType);
        likeService.like(userId, targetId ,targetType);
    }

    @PostMapping("/unLike")
    @Operation(summary = "取消点赞",description = "具体参数解释和 点赞 的api一样")
    public void unlikeArticle(HttpServletRequest request,
                              @RequestParam @NotNull Long targetId,
                              @RequestParam @NotNull String targetType) {
        //获取真实用户id
        Long userId = JwtUtil.getUserId(request);
        log.info("取消点赞：userId = {}, articleId = {}, targetType = {}", userId, targetId,targetType);
        likeService.unlike(userId, targetId ,targetType);
    }

    @GetMapping("/checkLike")
    @Operation(summary = "查询数据库，是否点赞过", description = "具体参数解释和 点赞 的api一样")
    public boolean checkLike(HttpServletRequest request,
                             @RequestParam @NotNull Long targetId,
                             @RequestParam @NotNull String targetType){
        Long userId = JwtUtil.getUserId(request);
        return likeService.checkLike(userId, targetId, targetType);
    }
}