package com.doublez.kc_forum.common.pojo.redis;

import lombok.Data;

@Data
public class UserCacheDTO {
    private Long id;
    private String userName;
    private String nickName;
    private String phone;
    private String email;
    private Integer gender;
    private String avatarUrl;
}
