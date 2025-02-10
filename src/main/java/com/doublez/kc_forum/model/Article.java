package com.doublez.kc_forum.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

//不需要加@Component，用到的时候需要创建一个新的对象而不是注入！！！
@Data
@Repository
public class Article {
    /**
     *                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *                            board_id BIGINT NOT NULL,
     *                            user_id BIGINT NOT NULL,
     *                            title VARCHAR(100) NOT NULL COMMENT '标题',
     *                            content TEXT NOT NULL COMMENT '正文内容',
     *                            visit_count INT DEFAULT 0 COMMENT '访问次数',
     *                            reply_count INT DEFAULT 0 COMMENT '回复数量',
     *                            like_count INT DEFAULT 0 COMMENT '点赞数量',
     *                            is_top TINYINT DEFAULT 0 COMMENT '置顶标记',
     *                            state TINYINT NOT NULL DEFAULT 0 COMMENT '状态',
     *                            delete_state TINYINT NOT NULL DEFAULT 0 COMMENT '删除状态',
     *                            create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     *                            update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     *                            FOREIGN KEY (board_id) REFERENCES board(id),
     *                            FOREIGN KEY (user_id) REFERENCES user(id)
     */
    @TableId( value = "id",type = IdType.AUTO)
    private Long id;
    private Long boardId;
    private Long userId;
    private String title;
    private String content;
    private Integer visitCount;
    private Integer replyCount;
    private Integer likeCount;
    private Byte isTop;
    private Byte state;
    private Byte deleteState;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
