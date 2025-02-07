package com.doublez.kc_forum.common.pojo.response;

import lombok.Data;

@Data
public class UserLoginResponse {
    private String Authorization;
    private Long userId;
}
