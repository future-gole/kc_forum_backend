package com.doublez.kc_forum.service.impl;

import com.doublez.kc_forum.model.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ArticleServiceImplTest {
    @Autowired
    ArticleServiceImpl articleService;

    @Test
    void createArtical() {
        Article article = new Article();
        article.setTitle("title");
        article.setContent("content");
        article.setUserId(1L);
        article.setBoardId(1L);
        articleService.createArtical(article);
        System.out.println("更新成功");
    }
}