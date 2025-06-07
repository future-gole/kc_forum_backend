package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.request.UpdateArticleRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleDetailResponse;
import com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO;
import com.doublez.kc_forum.common.pojo.response.ViewArticleResponse;
import com.doublez.kc_forum.model.Article;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface IArticleService {

    Long getUserId(Long articleId);

    /**
     * 发布帖子
     * 对应3条sql语句，文章表的insert和用户表、版块表的update
     * 并且需要添加事务，保证同时成功，同时失败
     * @param article
     * @return
     */
    @Transactional
    public void createArticle(Article article);

    /**
     * 通过boardid查询其board下的所有article
     * 组装成为ViewArticlesResponse返回
     * @param BoardId
     * @return  List<ArticleMetaCacheDTO>
     */
    List<ArticleMetaCacheDTO> getAllArticlesByBoardId(@RequestParam(required = false)Long BoardId);

    /**
     * 通过boardid分页查询其board下的所有article
     * @param boardId
     * @param currentPage
     * @param pageSize
     * @return
     */
    public ViewArticleResponse getArticleCards(Long boardId, int currentPage, int pageSize);
    /**
     * 根据id获取帖子详情
     * @param id
     * @return
     */
    ArticleDetailResponse getArticleDetailById(Long userId, Long id);

    /**
     * 根据用户id获得其帖子详情
     * @param userId
     * @return
     */
    List<ArticleMetaCacheDTO> getAllArticlesByUserId(Long userId);
    /**
     * 更新帖子
     * @param updateArticleRequest
     * @return
     */
    boolean updateArticle(UpdateArticleRequest updateArticleRequest);

    boolean deleteArticle(Long id);

    int updateLikeCount(Long targetId, int increment);
}
