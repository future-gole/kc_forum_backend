package com.doublez.kc_forum.common.pojo.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ViewArticlesResponse {
    private Long id;
    private Long boardId;
    private Long userId;
    private String title;
    private Integer visitCount;
    private Integer replyCount;
    private Integer likeCount;
    private Byte isTop;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    //关联对象
    private UserArticleResponse user;
}
