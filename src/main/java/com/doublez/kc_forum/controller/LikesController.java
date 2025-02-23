package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.utiles.AuthUtils;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.service.impl.LikesServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
@Slf4j
@RestController
@RequestMapping("/likes")
public class LikesController {

    @Autowired
    private LikesServiceImpl likeService;

    @PostMapping("/addLike")
    public void likeArticle(HttpServletRequest request,
                            @RequestParam @NotNull Long targetId,
                            @RequestParam @NotNull String targetType) {
        //获取真实用户id
        Long userId = JwtUtil.getUserId(request);
        log.info("新增点赞：userId = {}, articleId = {}, targetType = {}", userId, targetId,targetType);
        likeService.like(userId, targetId ,targetType);
    }

    @PostMapping("/unLike")
    public void unlikeArticle(HttpServletRequest request,
                              @RequestParam @NotNull Long targetId,
                              @RequestParam @NotNull String targetType) {
        //获取真实用户id
        Long userId = JwtUtil.getUserId(request);
        log.info("取消点赞：userId = {}, articleId = {}, targetType = {}", userId, targetId,targetType);
        likeService.unlike(userId, targetId ,targetType);
    }

    @GetMapping("/checkLike")
    public boolean checkLike(HttpServletRequest request,
                             @RequestParam @NotNull Long targetId,
                             @RequestParam @NotNull String targetType){
        Long userId = JwtUtil.getUserId(request);
        return likeService.checkLike(userId, targetId, targetType);
    }
}