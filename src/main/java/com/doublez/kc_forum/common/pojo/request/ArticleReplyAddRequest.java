package com.doublez.kc_forum.common.pojo.request;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleReplyAddRequest {
    @NotNull(message = "帖子id不能为空,请联系管理员")
    @Parameter(description = "帖子id")
    private Long articleId;//关联帖⼦编号
    @NotNull(message = "楼主id不能为空,请联系管理员")
    @Parameter(description = "楼主id")
    private Long postUserId;//楼主⽤⼾，关联⽤⼾编号
    //可以为空，判断楼中楼
    private Long replyId;//关联回复编号，⽀持楼中楼
//    @NotNull(message = "用户id不能为空,请联系管理员")
//    private Long replyUserId;//楼主下的回复⽤⼾编号，⽀持楼中楼
    @NotBlank(message = "回帖内容不能为空")
    @Parameter(description = "回贴内容id")
    private String content;//回贴内容
}
