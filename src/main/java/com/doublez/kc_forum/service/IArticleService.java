package com.doublez.kc_forum.service;

import com.doublez.kc_forum.model.Article;
import org.springframework.transaction.annotation.Transactional;

public interface IArticleService {

    /**
     * 发布帖子
     * 对应3条sql语句，文章表的insert和用户表、版块表的update
     * 并且需要添加事务，保证同时成功，同时失败
     * @param article
     * @return
     */
    @Transactional
    public void createArtical(Article article);
}
