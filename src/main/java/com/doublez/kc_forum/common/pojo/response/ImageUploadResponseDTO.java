package com.doublez.kc_forum.common.pojo.response;

import lombok.Data;

@Data
public class ImageUploadResponseDTO {
    private Long imageId;
    private String url;
    private String fileName;
    private String fileType;
    private Integer fileSize;

    // 用于前端显示的完整URL
    private String fullUrl;
}