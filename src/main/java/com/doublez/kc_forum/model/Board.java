package com.doublez.kc_forum.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

//不需要加@Component，用到的时候需要创建一个新的对象而不是注入！！！
@Data
@Repository
public class Board {
    /**
     *                          id BIGINT AUTO_INCREMENT PRIMARY KEY, 1 "ssa" "hhh" NULL 2 1 0 0
     *                          name VARCHAR(50) NOT NULL COMMENT '版块名称',
     *                          description VARCHAR(200),
     *                          article_count BIGINT DEFAULT 0 COMMENT '发帖数量',
     *                          department_id BIGINT COMMENT '所属部门（NULL为全站）',
     *                          visibility TINYINT NOT NULL DEFAULT 1 COMMENT '可见性 1全校 2科创内 3部门内',
     *                          post_level TINYINT NOT NULL DEFAULT 1 COMMENT '发帖权限 1干事 2部长 3主席',
     *                          sort_priority INT DEFAULT 0 COMMENT '排序优先级',
     *                          state TINYINT NOT NULL DEFAULT 0 COMMENT ''状态 0正常 1禁用',
     *                          delete_state TINYINT NOT NULL DEFAULT 0 COMMENT '删除状态',
     *                          create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     *                          update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     *                          FOREIGN KEY (department_id) REFERENCES department(id)
     */
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Long articleCount ;
    private Long departmentId;
    private Byte visibility;
    private Byte postLevel;
    private Integer sortPriority;
    private Byte state;
    private Byte deleteState;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
