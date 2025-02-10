package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.pojo.request.UpdateArticleRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleDetailResponse;
import com.doublez.kc_forum.common.pojo.response.ViewArticlesResponse;
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
    public void createArtical(Article article);

    /**
     * 通过boardid查询其board下的所以article
     * 组装成为ViewArticlesResponse返回
     * @param id
     * @return  List<ViewArticlesResponse>
     */
    List<ViewArticlesResponse> getAllArticlesByBoardId(@RequestParam(required = false)Long id);

    /**
     * 根据id获取帖子详情
     * @param id
     * @return
     */
    ArticleDetailResponse getArticleDetailById(Long userId, Long id);

    boolean updateArticle(UpdateArticleRequest updateArticleRequest);
}
