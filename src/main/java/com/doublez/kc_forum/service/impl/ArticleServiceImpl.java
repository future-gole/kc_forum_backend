package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.mapper.BoardMapper;
import com.doublez.kc_forum.mapper.UserMapper;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.service.IArticleService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;

@Slf4j
@Service
public class ArticleServiceImpl implements IArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private BoardServiceImpl boardServiceImpl;//注意不可用造成循环引用

    @Autowired
    private UserServiceImpl userServiceImpl;

    @Transactional
    @Override
    public void createArtical(Article article) {
        //非空校验
        if(article == null || article.getUserId() == null
                || article.getBoardId() == null
                || !StringUtils.hasText(article.getTitle())
                || !StringUtils.hasText(article.getContent())){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        //赋予默认值,好像不需要这样，数据库的性能更快
//        article.setLikeCount(0);
//        article.setVisitCount(0);
//        article.setReplyCount(0);
//        article.setState((byte)0);
//        article.setDeleteState((byte)0);
//        article.setCreateTime(LocalDateTime.now());

        //插入article
        int articleRow  = articleMapper.insert(article);
        if (articleRow == 0){
            log.warn(ResultCode.FAILED_CREATE.toString());
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CREATE));
        }

        //更新用户发帖数量
        userServiceImpl.updateOneArticleCountById(article.getUserId());

        //更新板块发帖数量
        boardServiceImpl.updateOneArticleCountById(article.getBoardId());

        //打印日志
        log.info(ResultCode.SUCCESS.toString()+article.getId()+"发帖成功"
                +"用户id："+ article.getUserId() + "板块id: " + article.getBoardId());

    }
}
