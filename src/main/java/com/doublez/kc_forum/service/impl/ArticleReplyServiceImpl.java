package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.common.pojo.response.UserArticleResponse;
import com.doublez.kc_forum.common.pojo.response.ViewArticleReplyResponse;
import com.doublez.kc_forum.common.utiles.IsEmptyClass;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.mapper.ArticleReplyMapper;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.ArticleReply;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IArticleReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleReplyServiceImpl implements IArticleReplyService{

    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private UserServiceImpl userServiceImpl;

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
            throw new ApplicationException(Result.failed(ResultCode.FAILED_ARTICLE_NOT_EXISTS));//帖子不存在
        }
        if( article.getDeleteState() == 1 || article.getState() == 1) {
            log.info(ResultCode.FAILED_ARTICLE_BANNED.toString());
            throw new ApplicationException(Result.failed(ResultCode.FAILED_ARTICLE_BANNED));//被删除或者禁言
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
        log.info("回帖成功, 回帖id: {} 用户id：{} 帖子id: {}", articleReply.getId(), articleReply.getPostUserId(), articleReply.getArticleId());

    }

    @Override
    public List<ViewArticleReplyResponse> getArticleReply(Long articleId) {
        //1. 查询ArticleReply表
        List<ArticleReply> articleReplies = articleReplyMapper.selectList(new LambdaQueryWrapper<ArticleReply>()
                .select(ArticleReply::getId, ArticleReply::getArticleId, ArticleReply::getReplyId,
                        ArticleReply::getPostUserId, ArticleReply::getReplyUserId, ArticleReply::getContent,
                        ArticleReply::getLikeCount,ArticleReply::getCreateTime)
                .eq(ArticleReply::getDeleteState, 0)
                .eq(ArticleReply::getState, 0)
                .eq(ArticleReply::getArticleId, articleId));
        //为空返回
        if (articleReplies == null || articleReplies.isEmpty()) {
            log.info("回复贴为空");
            return Collections.emptyList();
        }
        //2. 提取所有 userId
        List<Long> userIds = articleReplies.stream()
                .map(ArticleReply::getPostUserId)
                .distinct()//去重
                .toList();
        Map<Long, User> userMap = userServiceImpl.selectUserInfoByIds(userIds);

        //3. 组装数据

        List<ViewArticleReplyResponse> viewArticleReplysResponse = articleReplies.stream().map(articleReply -> {
            User user = userMap.get(articleReply.getPostUserId());
            //判断用户是否存在
            IsEmptyClass.Empty(user,ResultCode.FAILED_USER_NOT_EXISTS,articleReply.getArticleId());

            UserArticleResponse userArticleResponse = copyProperties(user,UserArticleResponse.class);
            ViewArticleReplyResponse viewArticleReplyResponse = copyProperties(articleReply, ViewArticleReplyResponse.class);

            viewArticleReplyResponse.setUser(userArticleResponse);
            return viewArticleReplyResponse;
        }).toList();

        log.info("{}:查询回复贴成功", ResultCode.SUCCESS.getMessage());
        return viewArticleReplysResponse;
    }

    @Override
    public  int updateLikeCount(Long targetId, int increment){
        return articleReplyMapper.update(new LambdaUpdateWrapper<ArticleReply>()
                .eq(ArticleReply::getId,targetId)
                .eq(ArticleReply::getDeleteState,0)
                .eq(ArticleReply::getState,0)
                .setSql("like_count = like_count + " + increment));
    }

    @Override
    public int deleteArticleReply(Long articleId) {
        return articleReplyMapper.update(new LambdaUpdateWrapper<ArticleReply>()
                .set(ArticleReply::getDeleteState,1)
                .eq(ArticleReply::getId,articleId));
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
