package com.doublez.kc_forum.common.pojo.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
@AllArgsConstructor
@Data
public class ViewArticleResponse {
    private List<ArticleMetaCacheDTO> record;
    private Long total;
}
