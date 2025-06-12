package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.mapper.ArticleReplyMapper;
import com.doublez.kc_forum.mapper.LikesMapper;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.ArticleReply;
import com.doublez.kc_forum.model.Likes;
import com.doublez.kc_forum.service.IArticleReplyService;
import com.doublez.kc_forum.service.IArticleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.doublez.kc_forum.common.config.AsyncConfig.DB_PERSISTENCE_EXECUTOR;
import static com.doublez.kc_forum.service.ILikesService.TARGET_TYPE_ARTICLE;
import static com.doublez.kc_forum.service.ILikesService.TARGET_TYPE_REPLY;

@Slf4j
@Service
public class DBAsyncPopulationService {
    // todo为失败的数据库操作实现重试机制或死信队列
    @Autowired
    private  LikesMapper likesMapper;
    @Autowired
    private ArticleReplyMapper articleReplyMapper;
    @Autowired
    private ArticleMapper articleMapper;
    /**
     * 异步持久化点赞状态到like数据库。
     */
    @Async(DB_PERSISTENCE_EXECUTOR)
    public void persistLikeStateAsync(Long userId, Long targetId, String targetType, boolean isLiked) {
        try {
            if (isLiked) { // 点赞操作
                // 检查数据库中是否已存在该点赞记录，避免重复插入（尽管Redis层面已处理，DB层面也做个保险）
                Likes existingLike = likesMapper.selectOne(new LambdaQueryWrapper<Likes>()
                        .eq(Likes::getUserId, userId)
                        .eq(Likes::getTargetId, targetId)
                        .eq(Likes::getTargetType, targetType));
                if (existingLike == null) {
                    Likes like = new Likes();
                    like.setUserId(userId);
                    like.setTargetId(targetId);
                    like.setTargetType(targetType);
                    like.setCreateTime(LocalDateTime.now()); // 记录点赞时间
                    likesMapper.insert(like);
                    log.info("[DB异步] 持久化点赞记录成功, userId: {}, target: {}:{}", userId, targetType, targetId);
                } else {
                    log.info("[DB异步] 点赞记录已存在于数据库, userId: {}, target: {}:{}", userId, targetType, targetId);
                }
            } else { // 取消点赞操作
                int deletedRows = likesMapper.delete(new LambdaQueryWrapper<Likes>()
                        .eq(Likes::getUserId, userId)
                        .eq(Likes::getTargetId, targetId)
                        .eq(Likes::getTargetType, targetType));
                if (deletedRows > 0) {
                    log.info("[DB异步] 从数据库删除点赞记录成功, userId: {}, target: {}:{}", userId, targetType, targetId);
                } else {
                    log.warn("[DB异步] 尝试从数据库删除点赞记录，但未找到匹配项, userId: {}, target: {}:{}", userId, targetType, targetId);
                }
            }
        } catch (Exception e) {
            log.error("[DB异步] 持久化点赞状态失败, userId: {}, target: {}:{}, isLiked: {}. 错误: {}",
                    userId, targetType, targetId, isLiked, e.getMessage(), e);

        }
    }
    /**
     * 异步持久化点赞信息到article/reply数据库。
     */
    @Async(DB_PERSISTENCE_EXECUTOR)
    public void updateLikeCountInDb(Long targetId, String targetType, int increment) {
        if (TARGET_TYPE_ARTICLE.equals(targetType)) {
            updateLikeCountWithArticle(targetId, increment);
        } else if (TARGET_TYPE_REPLY.equals(targetType)) {
            updateLikeCountWithReply(targetId, increment);
        } else {
            log.error("取消/新增点赞失败，targetId: {}, targetType: {}",targetId, targetType);
        }
    }

    @Async(DB_PERSISTENCE_EXECUTOR)
    public void updateLikeCountWithArticle(Long targetId, int increment){
        int update = articleMapper.update(new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, targetId)
                .eq(Article::getDeleteState, 0)
                .eq(Article::getState, 0)
                .setSql("like_count = like_count + " + increment));
        if(update != 1) {
            log.error("更新 文章 点赞数失败，targetId:{},increment:{}",targetId,increment);
        }
    }
    @Async(DB_PERSISTENCE_EXECUTOR)
    public  void updateLikeCountWithReply(Long targetId, int increment){
        int update = articleReplyMapper.update(new LambdaUpdateWrapper<ArticleReply>()
                .eq(ArticleReply::getId, targetId)
                .eq(ArticleReply::getDeleteState, 0)
                .eq(ArticleReply::getState, 0)
                .setSql("like_count = like_count + " + increment));
        if(update != 1) {
            log.error("更新 回复 点赞数失败，targetId:{},increment:{}",targetId,increment);
        }
    }

    @Async(DB_PERSISTENCE_EXECUTOR)
    public void updateReplyChildrenCountInDb(Long replyId,int increment) {
        int row = articleReplyMapper.update(null, new LambdaUpdateWrapper<ArticleReply>()
                .setSql("children_count = children_count + " + increment) // 数据库直接进行加减操作
                .eq(ArticleReply::getId, replyId));
        if(row != 1) {
            log.error("回复贴的childrenCount 更新失败，replyId: {},increment:{}", replyId,increment);
        }
    }

    @Async(DB_PERSISTENCE_EXECUTOR)
    public void updateArticleVisitCount(Long articleId) {
        articleMapper.update(new LambdaUpdateWrapper<Article>()
                .setSql("visit_count = visit_count + 1")
                .eq(Article::getId, articleId));
    }
    @Async(DB_PERSISTENCE_EXECUTOR)
    public void updateArticleReplyCount(Long articleId,int increment) {
        int update = articleMapper.update(new LambdaUpdateWrapper<Article>()
                .setSql("reply_count = reply_count +" + increment).eq(Article::getId, articleId));
        if(update != 1) {
            log.error("文章的ReplyCount 更新失败，articleId: {},increment:{}", articleId,increment);
        }
    }
}
