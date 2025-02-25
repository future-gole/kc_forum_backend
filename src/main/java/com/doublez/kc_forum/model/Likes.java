package com.doublez.kc_forum.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
@Data
@Repository
@TableName("likes")
public class Likes {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long targetId;
    private String targetType;
    @JsonFormat(pattern = "yyyy-mm-dd")
    private LocalDateTime createTime;
}
