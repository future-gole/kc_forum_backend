package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleReplyMetaCacheDTO;
import com.doublez.kc_forum.common.pojo.response.ViewArticleReplyResponse;

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
     * @param currentPage
     * @param pageSize
     * @return
     */
    ViewArticleReplyResponse getArticleReply(Long articleId, Integer currentPage, Integer pageSize);

//    /**
//     * 更新回复贴点赞
//     * @param targetId
//     * @param increment
//     * @return
//     */
//    int updateLikeCount(Long targetId, int increment);

    int deleteArticleReply(Long userId,Long articleReplyId,Long articleId);

    List<ArticleReplyMetaCacheDTO> getChildrenReplyByReplyId(Long replyId,Integer currentPage, Integer pageSize);
}
