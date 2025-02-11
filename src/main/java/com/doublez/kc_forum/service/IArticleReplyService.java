package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.model.ArticleReply;

public interface IArticleReplyService {

    /**
     * 创建回复帖子
     * @param articleReply 回复帖子实体类
     */
    void createArticleReply(ArticleReplyAddRequest articleReply);
}
