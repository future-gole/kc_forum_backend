package com.doublez.kc_forum.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@Data
public class ArticleReply {
    /**
     * CREATE TABLE article_reply (
     *                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *                                  article_id BIGINT NOT NULL,
     *                                  post_user_id BIGINT NOT NULL,
     *                                  reply_id BIGINT,
     *                                  reply_user_id BIGINT,
     *                                  content VARCHAR(500) NOT NULL,
     *                                  like_count INT DEFAULT 0,
     *                                  state TINYINT NOT NULL DEFAULT 0 COMMENT '状态',
     *                                  delete_state TINYINT NOT NULL DEFAULT 0 COMMENT '删除状态',
     *                                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     *                                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     *                                  FOREIGN KEY (article_id) REFERENCES article(id),
     *                                  FOREIGN KEY (post_user_id) REFERENCES user(id)
     * ) COMMENT='帖子回复表'
     */
    @TableId(value = "id",type = IdType.AUTO)//确保数据库id自增！！！
    private Long id;
    private Long articleId;//关联帖⼦编号
    private Long postUserId;//楼主⽤⼾，关联⽤⼾编号
    private Long replyId;//关联回复编号，⽀持楼中楼
    private Long replyUserId;//楼主下的回复⽤⼾编号，⽀持楼中楼
    private String content;
    private Integer likeCount;
    private Byte state; // Use Byte for TINYINT
    private Byte deleteState; // Use Byte for TINYINT
    @JsonFormat(pattern = "yyyy-mm-dd")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-mm-dd")
    private LocalDateTime updateTime;
}
