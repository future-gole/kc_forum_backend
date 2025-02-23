package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.common.pojo.response.ViewArticleReplyResponse;
import com.doublez.kc_forum.model.ArticleReply;

import java.util.List;

public interface IArticleReplyService {

    /**
     * 创建回复帖子
     * @param articleReply 回复帖子实体类
     */
    void createArticleReply(ArticleReplyAddRequest articleReply);

    /**
     * 获取所有回复贴
     * @param articleId
     * @return
     */
    List<ViewArticleReplyResponse> getArticleReply(Long articleId);

    /**
     * 更新回复贴点赞
     * @param targetId
     * @param increment
     * @return
     */
    int updateLikeCount(Long targetId, int increment);
}
