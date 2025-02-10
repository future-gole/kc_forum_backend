package com.doublez.kc_forum.common.pojo.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleDetailResponse {

    private Long id;
    private Long boardId;
    private Long userId;
    private String title;
    private String content;
    private Integer visitCount;
    private Integer replyCount;
    private Integer likeCount;
    @JsonFormat(pattern = "yyyy-mm-dd HH:mm:ss")
    private LocalDateTime createTime;
    //关联对象
    private UserArticleResponse user;
    //判断是否是作者
    private boolean  isOwn = false;
}
