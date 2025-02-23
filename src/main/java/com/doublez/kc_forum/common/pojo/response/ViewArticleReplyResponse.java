package com.doublez.kc_forum.common.pojo.response;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ViewArticleReplyResponse {
    private Long id;
    private Long articleId;//关联帖⼦编号
    private Long postUserId;//楼主⽤⼾，关联⽤⼾编号
    private Long replyId;//关联回复编号，⽀持楼中楼
    private Long replyUserId;//楼主下的回复⽤⼾编号，⽀持楼中楼
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
    //关联对象
    private UserArticleResponse user;
}
