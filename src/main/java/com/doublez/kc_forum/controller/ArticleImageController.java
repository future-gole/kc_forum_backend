package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.pojo.response.ImageUploadResponseDTO;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.service.impl.ArticleImageServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@RestController
@RequestMapping("/article")
@Tag(name = "文章图片管理", description = "文章图片上传、查询和删除API")
@Slf4j
public class ArticleImageController {

    @Autowired
    private ArticleImageServiceImpl articleImageService;

    @PostMapping("/{articleId}/images")
    @Operation(summary = "上传文章图片", description = "为指定文章上传图片")
    public ImageUploadResponseDTO uploadImage(
            HttpServletRequest request,
            @PathVariable @Parameter(description = "文章ID") Long articleId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder) {

        // 获取当前用户ID
        Long userId = JwtUtil.getUserId(request);
        log.info("用户 {} 为文章 {} 上传图片", userId, articleId);

        // 调用服务上传图片
        return articleImageService.uploadImage(articleId, file, displayOrder);
    }

    @GetMapping("/{articleId}/images")
    @Operation(summary = "获取文章图片列表", description = "获取指定文章的所有图片")
    public List<ImageUploadResponseDTO> getArticleImages(
            @PathVariable @Parameter(description = "文章ID") Long articleId) {

        log.info("获取文章 {} 的图片列表", articleId);
        return articleImageService.getArticleImages(articleId);
    }

    @DeleteMapping("/images/{imageId}")
    @Operation(summary = "删除文章图片", description = "删除指定的文章图片")
    public Result<Object> deleteImage(
            HttpServletRequest request,
            @PathVariable @Parameter(description = "图片ID") Long imageId) {

        // 获取当前用户ID
        Long userId = JwtUtil.getUserId(request);
        log.info("用户 {} 删除图片 {}", userId, imageId);

        boolean success = articleImageService.deleteImage(imageId);
        if (success) {
            return Result.sucess();
        } else {
            return Result.failed("删除图片失败");
        }
    }
}