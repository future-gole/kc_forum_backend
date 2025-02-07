package com.doublez.kc_forum.common.pojo.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    private String userName;
    @NotBlank(message = "密码不能为空")
    private String password;
    private String repeatPassword;
    @NotBlank(message = "昵称不能为空")
    private String nickName;
    // TODO
//    private String email;
}
