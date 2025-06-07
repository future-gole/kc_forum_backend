package com.doublez.kc_forum.service.impl;

import com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO;
import com.doublez.kc_forum.model.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class ArticleServiceImplTest {
    @Autowired
    ArticleServiceImpl articleService;

    @Test
    void createArticle() {
        Article article = new Article();
        article.setTitle("title");
        article.setContent("content");
        article.setUserId(1L);
        article.setBoardId(1L);
        articleService.createArticle(article);
        System.out.println("更新成功");
    }

    @Test
    void getAllArticlesByBoardId() {
        List<ArticleMetaCacheDTO> articles = articleService.getAllArticlesByBoardId(1L);
        System.out.println(articles.toString());
    }

    @Test
    void getUserId() {
        System.out.println(articleService.getUserId(1L));
    }
}