package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.response.ImageUploadResponseDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface IArticleImageService {

    /**
     * 上传文章图片
     * @param articleId 文章ID
     * @param file 上传的文件
     * @param displayOrder 显示顺序
     * @return 图片上传响应DTO
     */
    ImageUploadResponseDTO uploadImage(Long articleId, MultipartFile file, Integer displayOrder);

    /**
     * 获取文章的所有图片
     * @param articleId 文章ID
     * @return 图片列表
     */
    List<ImageUploadResponseDTO> getArticleImages(Long articleId);

    /**
     * 删除文章图片
     * @param imageId 图片ID
     * @return 是否删除成功
     */
    boolean deleteImage(Long imageId);
}