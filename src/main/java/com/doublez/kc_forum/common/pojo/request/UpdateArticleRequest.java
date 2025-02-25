package com.doublez.kc_forum.common.pojo.request;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateArticleRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 100,message = "标题长度不能超过100")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;
    @NotNull//数字是用NotNull，字符串才是NotBlank
    @Parameter(description = "帖子id")
    private Long id;
    //不可用由前端传，可以调api改掉
//    @NotNull
//    private Long articleUserId;

}
