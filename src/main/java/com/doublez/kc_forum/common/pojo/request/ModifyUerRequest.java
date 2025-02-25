package com.doublez.kc_forum.common.pojo.request;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModifyUerRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 20,message = "用户名长度不能超过20")
    private String userName;
    @NotBlank(message = "昵称不能为空")
    @Size(max = 20,message = "昵称不能超过20")
    private String nickName;
    private String phone;
    private String email;
    private Integer gender;
    private String remark;
}
