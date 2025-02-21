package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.mapper.ArticleReplyMapper;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.ArticleReply;
import com.doublez.kc_forum.service.IArticleReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ArticleReplyServiceImpl implements IArticleReplyService{

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private ArticleMapper articleMapper;
    @Override
    public void createArticleReply(ArticleReplyAddRequest articleReplyAddRequest) {
        if(articleReplyAddRequest == null || articleReplyAddRequest.getArticleId() == null
                || articleReplyAddRequest.getPostUserId() == null || articleReplyAddRequest.getArticleId() <= 0
                || !StringUtils.hasText(articleReplyAddRequest.getContent())) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }

        //判断帖子是否正常
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId,articleReplyAddRequest.getArticleId()));

        if(article == null) {
            log.error(ResultCode.FAILED_ARTICLE_NOT_EXISTS.toString());
            throw new ApplicationException(Result.failed(ResultCode.FAILED_ARTICLE_NOT_EXISTS));
        }
        if( article.getDeleteState() == 1 || article.getState() == 1) {
            log.info(ResultCode.FAILED_ARTICLE_BANNED.toString());
            throw new ApplicationException(Result.failed(ResultCode.FAILED_ARTICLE_BANNED));
        }
        //类型转化
        ArticleReply articleReply = copyProperties(articleReplyAddRequest,ArticleReply.class);
        //插入articleReply
        if(articleReplyMapper.insert(articleReply) != 1) {
            log.error(ResultCode.FAILED_CREATE.toString());
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CREATE));
        }

        //更新帖子回复数量+1
        articleMapper.update(new LambdaUpdateWrapper<Article>()
                .setSql("reply_count = reply_count + 1").eq(Article::getId,articleReplyAddRequest.getArticleId()));

        //打印日志
        log.info("回帖成功, 回帖id: "+articleReply.getId() +" 用户id："+ articleReply.getPostUserId() + " 帖子id: " + articleReply.getArticleId());

    }
    public  int updateLikeCount(Long targetId, int increment){
        return articleReplyMapper.update(new LambdaUpdateWrapper<ArticleReply>()
                .eq(ArticleReply::getId,targetId)
                .eq(ArticleReply::getDeleteState,0)
                .eq(ArticleReply::getState,0)
                .setSql("like_count = like_count + " + increment));
    }

    // 类型转化抽取出来的通用方法
    private <Source, Target> Target copyProperties(Source source, Class<Target> targetClass) {
        try {
            Target target = targetClass.getDeclaredConstructor().newInstance(); // 使用反射创建实例
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            log.error("类型转换失败: {} -> {}", source.getClass().getName(), targetClass.getName(), e);
            throw new ApplicationException(Result.failed(ResultCode.ERROR_TYPE_CHANGE));
        }
    }
}
