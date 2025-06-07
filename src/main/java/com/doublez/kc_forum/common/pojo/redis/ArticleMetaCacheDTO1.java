package com.doublez.kc_forum.common.pojo.redis;

import com.doublez.kc_forum.common.pojo.response.UserArticleResponse;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ArticleMetaCacheDTO1 {
    private Long id;
    private Long boardId;
    private Long userId;
    private String title;
    private Integer replyCount;
    private Byte isTop;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
