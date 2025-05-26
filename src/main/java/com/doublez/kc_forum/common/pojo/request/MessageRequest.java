package com.doublez.kc_forum.common.pojo.request;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NonNull;

@Data
public class MessageRequest {
    @NotBlank(message = "内容不能为空")
    @Size(max = 2000)
    @Parameter(description = "内容")
    private String content;
    @NonNull
    @Parameter(description = "接收用户id")
    private Long receiveUserId;
}
