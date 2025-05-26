package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.mapper.LikesMapper;
import com.doublez.kc_forum.model.Likes;
import com.doublez.kc_forum.service.ILikesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
@Slf4j
@Service
public class LikesServiceImpl implements ILikesService {

    @Autowired
    private LikesMapper likeMapper;

    @Autowired
    private ArticleServiceImpl articleService;  // 注入 ArticleService

    @Autowired
    private ArticleReplyServiceImpl articleReplyService; // 注入 ArticleReplyService

    private static final String TARGET_TYPE_ARTICLE = "article";
    private static final String TARGET_TYPE_REPLY = "reply";

    private Likes isLikes(Long userId, Long targetId, String targetType){
        return likeMapper.selectOne(new LambdaQueryWrapper<Likes>().eq(Likes::getUserId, userId)
                .eq(Likes::getTargetId, targetId).eq(Likes::getTargetType, targetType));
    }
    @Override
    @Transactional
    public void like(Long userId, Long targetId, String targetType) {
        // 1. 查询数据库，确认是否已点赞
        Likes existingLike = isLikes(userId, targetId, targetType);
        //判断
        if (existingLike != null) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CHANGE_LIKE));
        }
        //todo检查文章和回复是否有效
        // 2. 插入点赞记录
        Likes like = new Likes();
        like.setUserId(userId);
        like.setTargetId(targetId);
        like.setTargetType(targetType);
        like.setCreateTime(LocalDateTime.now());
        likeMapper.insert(like);

        // 3. 更新 article 或 reply 的 like_count
        if(updateLikeCount(targetId, targetType, 1) != 1){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CHANGE_LIKE));
        }
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long targetId, String targetType) {
        // 1. 查询数据库，确认是否已点赞
        Likes existingLike = isLikes(userId, targetId, targetType);

        if (existingLike == null) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CHANGE_LIKE));
        }

        // 2. 删除点赞记录
        likeMapper.deleteById(existingLike.getId());

        // 3. 更新 article 或 reply 的 like_count
        if(updateLikeCount(targetId, targetType, -1) != 1){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CHANGE_LIKE));
        }
    }

    @Override
    public boolean checkLike(Long userId, Long targetId, String targetType) {
        // 1. 查询数据库，确认是否已点赞
        Likes existingLike = isLikes(userId, targetId, targetType);
        return existingLike != null;
    }

    private int updateLikeCount(Long targetId, String targetType, int increment) {
        if (TARGET_TYPE_ARTICLE.equals(targetType)) {
            return  articleService.updateLikeCount(targetId, increment);
        } else if (TARGET_TYPE_REPLY.equals(targetType)) {
            return articleReplyService.updateLikeCount(targetId, increment);
        } else {
            log.error("取消/新增点赞失败，targetId: {}, targetType: {}",targetId, targetType);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CHANGE_LIKE));
        }
    }
}