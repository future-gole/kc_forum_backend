package com.doublez.kc_forum.common.pojo.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArticleAddRequest {
    @NotBlank(message = "标题不能为空")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;
//    @NotBlank(message = "板块id不能为空")
    private Long boardId;
}
