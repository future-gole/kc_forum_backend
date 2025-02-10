package com.doublez.kc_forum.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


//不需要加@Component，用到的时候需要创建一个新的对象而不是注入！！！
@Data
public class User {
    /**
     * CREATE TABLE user (
     *                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
     *                         user_name VARCHAR(50) NOT NULL UNIQUE COMMENT '登录账号',
     *                         password VARCHAR(60) NOT NULL COMMENT 'BCrypt加密密码',
     *                         nick_name VARCHAR(50) NOT NULL COMMENT '显示名称',
     *                         phone VARCHAR(20),
     *                         email VARCHAR(50),
     *                         gender TINYINT DEFAULT 2 COMMENT '性别 0女 1男 2保密',
     *                         avatar_url VARCHAR(255) DEFAULT '/default_avatar.png' COMMENT '头像路径',
     *                         article_count BIGINT DEFAULT 0 COMMENT '发帖数量',
     *                         remark VARCHAR(200) COMMENT '自我介绍',
     *                         state TINYINT NOT NULL DEFAULT 0 COMMENT '状态',
     *                         delete_state TINYINT NOT NULL DEFAULT 0 COMMENT '删除状态',
     *                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     *                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
     * ) COMMENT='用户信息表';
     */
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    private String userName;
    @JsonIgnore
    private String password;
    private String nickName;
    private String phone;
    private String email;
    private Integer gender;
    private String avatarUrl;
    private Integer articleCount;
    private String remark;
    private Integer state;
    @JsonIgnore
    private Integer deleteState;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
