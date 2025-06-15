package com.doublez.kc_forum.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Data
@Repository
public class Message {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;

    private Long postUserId;
    private Long receiveUserId;
    private String content;

    private Byte state;
    private Byte deleteState;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
