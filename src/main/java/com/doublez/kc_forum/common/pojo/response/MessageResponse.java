package com.doublez.kc_forum.common.pojo.response;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class MessageResponse {
    private Long id;
    private Long postUserId;
    private Long receiveUserId;
    private String content;

    private Byte state;

    private LocalDateTime createTime;

}
