package com.doublez.kc_forum.model;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("article_image")
public class ArticleImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String filePath;
    private String fileName;
    private Integer fileSize;
    private String fileType;
    private Integer displayOrder;
    private LocalDateTime createTime;
}