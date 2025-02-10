package com.doublez.kc_forum.common.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserArticleResponse {
    private Long id;
    private String userName;
    private String nickName;
    private String phone;
    private String email;
    private Integer gender;
    private String avatarUrl;
}
