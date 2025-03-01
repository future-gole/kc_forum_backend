package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.doublez.kc_forum.common.pojo.response.ImageUploadResponseDTO;
import com.doublez.kc_forum.mapper.ArticleImageMapper;
import com.doublez.kc_forum.model.ArticleImage;
import com.doublez.kc_forum.service.IArticleImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArticleImageServiceImpl extends ServiceImpl<ArticleImageMapper, ArticleImage> implements IArticleImageService {

    @Value("${upload.base-path}")
    private String basePath;

    @Value("${upload.base-url}")
    private String baseUrl;

    /**
     * 上传文章图片
     * 1. 生成唯一文件名
     * 2. 创建存储目录
     * 3. 保存文件到文件系统
     * 4. 保存图片信息到数据库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageUploadResponseDTO uploadImage(Long articleId, MultipartFile file, Integer displayOrder) {
        try {
            // 获取原始文件名和扩展名
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);

            // 生成唯一文件名
            String uniqueFileName = "article_" + articleId + "_" + UUID.randomUUID().toString() + "." + fileExtension;

            // 按年/月/日构建存储路径
            LocalDate today = LocalDate.now();
            String relativePath = String.format("/articles/%d/%02d/%02d/",
                    today.getYear(), today.getMonthValue(), today.getDayOfMonth());

            // 创建目录
            Path directoryPath = Paths.get(basePath, relativePath);
            log.info("Creating directory: {}", directoryPath); // 打印目录创建路径
            Files.createDirectories(directoryPath);

            // 完整文件路径
            String fullFilePath = relativePath + uniqueFileName;
            Path filePath = Paths.get(basePath, fullFilePath);
            log.info("Writing file to: {}", filePath); // 打印文件写入路径

            // 保存文件
            Files.write(filePath, file.getBytes());

            // 创建数据库记录
            ArticleImage articleImage = new ArticleImage();
            articleImage.setArticleId(articleId);
            articleImage.setFilePath(fullFilePath);
            articleImage.setFileName(originalFilename);
            articleImage.setFileSize((int)(file.getSize() / 1024)); // 转换为KB
            articleImage.setFileType(file.getContentType());
            articleImage.setDisplayOrder(displayOrder != null ? displayOrder : 0);
            articleImage.setCreateTime(LocalDateTime.now());

            // 保存到数据库 (使用MyBatis-Plus)
            this.save(articleImage);

            // 构建响应
            ImageUploadResponseDTO response = new ImageUploadResponseDTO();
            response.setImageId(articleImage.getId());
            response.setUrl(fullFilePath);
            response.setFileName(originalFilename);
            response.setFileType(file.getContentType());
            response.setFileSize(articleImage.getFileSize());
            response.setFullUrl(baseUrl + "/images" + fullFilePath);

            return response;
        } catch (IOException e) {
            log.error("Failed to upload image", e); // 打印异常信息
            throw new RuntimeException("Failed to upload image", e);
        }
    }

    /**
     * 获取文章的所有图片
     */
    @Override
    public List<ImageUploadResponseDTO> getArticleImages(Long articleId) {
        // 使用MyBatis-Plus的LambdaQueryWrapper查询
        LambdaQueryWrapper<ArticleImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleImage::getArticleId, articleId)
                .orderByAsc(ArticleImage::getDisplayOrder);

        List<ArticleImage> images = this.list(queryWrapper);

        return images.stream().map(img -> {
            ImageUploadResponseDTO dto = new ImageUploadResponseDTO();
            dto.setImageId(img.getId());
            dto.setUrl(img.getFilePath());
            dto.setFileName(img.getFileName());
            dto.setFileType(img.getFileType());
            dto.setFileSize(img.getFileSize());
            dto.setFullUrl(baseUrl + "/images" + img.getFilePath());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 删除文章图片
     * 1. 从数据库删除记录
     * 2. 从文件系统删除文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteImage(Long imageId) {
        // 先查询图片信息 (使用MyBatis-Plus)
        ArticleImage image = this.getById(imageId);
        if (image == null) {
            return false;
        }

        try {
            // 删除文件
            Path filePath = Paths.get(basePath + image.getFilePath());
            Files.deleteIfExists(filePath);

            // 删除数据库记录 (使用MyBatis-Plus)
            this.removeById(imageId);

            return true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete image", e);
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}