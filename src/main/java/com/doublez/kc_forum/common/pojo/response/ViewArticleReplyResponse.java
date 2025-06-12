package com.doublez.kc_forum.common.pojo.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
@Data
@AllArgsConstructor
public class ViewArticleReplyResponse {
    private List<ArticleReplyMetaCacheDTO> record;
    private Long topTotal;
}
