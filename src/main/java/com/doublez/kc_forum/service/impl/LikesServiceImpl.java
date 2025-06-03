package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.mapper.LikesMapper;
import com.doublez.kc_forum.model.Likes;
import com.doublez.kc_forum.service.IArticleReplyService;
import com.doublez.kc_forum.service.IArticleService;
import com.doublez.kc_forum.service.ILikesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class LikesServiceImpl implements ILikesService {


    private final LikesMapper likesMapper;

    private final RedisScript<List> likeScript;

    private final RedisScript<List> unlikeScript;

    private final StringRedisTemplate stringRedisTemplate;


    private IArticleService articleService;

    private IArticleReplyService articleReplyService;

    public static final String DB_PERSISTENCE_EXECUTOR = "dbPersistenceExecutor";


    @Autowired
    public LikesServiceImpl(StringRedisTemplate stringRedisTemplate,
                            @Qualifier("likeScript") RedisScript<List> likeScript,
                            @Qualifier("unlikeScript") RedisScript<List> unlikeScript,
                            LikesMapper likesMapper,
                            IArticleService articleService,
                            IArticleReplyService articleReplyService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.likeScript = likeScript;
        this.unlikeScript = unlikeScript;
        this.likesMapper = likesMapper;
        this.articleService = articleService;
        this.articleReplyService = articleReplyService;
    }


    private static final String TARGET_TYPE_ARTICLE = "article";
    private static final String TARGET_TYPE_REPLY = "reply";

    private Likes isLikes(Long userId, Long targetId, String targetType){
        return likesMapper.selectOne(new LambdaQueryWrapper<Likes>().eq(Likes::getUserId, userId)
                .eq(Likes::getTargetId, targetId).eq(Likes::getTargetType, targetType));
    }

    @Override
    public void like(Long userId, Long targetId, String targetType) {
        // todo: 检查 targetId 和 targetType 的有效性 (例如文章或回复是否存在)
        // if (!isTargetValid(targetId, targetType)) {
        //     throw new BusinessException(ResultCode.TARGET_NOT_FOUND);
        // }
        String userLikesSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, targetId);
        String targetLikeCountKey = RedisKeyUtil.getTargetLikeCountKey(targetType, targetId);
        //1。 更新redis
        try {
            @SuppressWarnings("unchecked") // Script returns List<Long>
            List<Long> results = (List<Long>) stringRedisTemplate.execute(
                    likeScript,
                    //构造keys的list，用于传入lua脚本
                    List.of(userLikesSetKey, targetLikeCountKey),
                    userId.toString()
            );

            if (results.size() < 2) {
                log.error("redis 点赞脚本执行异常，userId: {}, target: {}:{}, Results: {}",
                        userId, targetType, targetId, results);
                throw new SystemException(ResultCode.FAILED_OPERATION_REDIS_SCRIPT, "redis脚本执行异常");
            }

            long operationStatus = results.get(0); // 1 for new like, 0 for already liked
            long newOrCurrentCount = results.get(1);

            if (operationStatus == 0) {
                log.warn("用户 {} 已经对目标进行点赞，点赞失败 {}:{}. 当前数量: {}", userId, targetType, targetId, newOrCurrentCount);
                throw new BusinessException(ResultCode.FAILED_CHANGE_LIKE);
            }

            log.info("用户 {} 成功对目标进行点赞 {}:{}. 当前数量: {}", userId, targetType, targetId, newOrCurrentCount);
            LikesServiceImpl self = (LikesServiceImpl)AopContext.currentProxy();
            self.persistLikeStateAsync(userId, targetId, targetType, true);

        } catch (BusinessException be) {
            throw be;//重新抛出
        } catch (Exception e) {
            log.error("[redis] 执行点赞操作异常 userId: {}, target: {}:{}. Error: {}",
                    userId, targetType, targetId, e.getMessage(), e);
            // Consider if a rollback or compensation is needed in Redis if script partially failed (though Lua is atomic)
            throw new SystemException(ResultCode.FAILED_OPERATION_REDIS_SCRIPT, e.getMessage());
        }
    }

    @Override
    public void unlike(Long userId, Long targetId, String targetType) {
        // todo: 检查 targetId 和 targetType 的有效性 (例如文章或回复是否存在)
        // if (!isTargetValid(targetId, targetType)) {
        //     throw new BusinessException(ResultCode.TARGET_NOT_FOUND);
        // }
        String userLikesSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, targetId);
        String targetLikeCountKey = RedisKeyUtil.getTargetLikeCountKey(targetType, targetId);
        //1. 更新redis
        try{
            @SuppressWarnings("unchecked")
            List<Long> results = (List<Long>)stringRedisTemplate.execute(
                    unlikeScript,
                    List.of(userLikesSetKey, targetLikeCountKey),
                    userId.toString()
            );
            if(results.size() < 2) {
                log.error("redis 点赞脚本执行异常，userId: {}, target: {}:{}, Results: {}",
                        userId, targetType, targetId, results);
                throw new SystemException(ResultCode.FAILED_OPERATION_REDIS_SCRIPT, "redis脚本执行异常");
            }
            long operationStatus = results.get(0);
            long newOrCurrentCount = results.get(1);
            //1.1 执行失败
            if (operationStatus == 0) {
                log.warn("用户 {} 未对目标进行点赞，取消失败 {}:{}. 当前数量: {}", userId, targetType, targetId, newOrCurrentCount);
                throw new BusinessException(ResultCode.FAILED_CHANGE_LIKE);
            }
            //1.2 执行成功
            log.info("用户 {} 成功对目标取消点赞 {}:{}. 当前数量: {}", userId, targetType, targetId, newOrCurrentCount);
            //2. 异步执行数据库操作
            LikesServiceImpl self = (LikesServiceImpl)AopContext.currentProxy();
            self.persistLikeStateAsync(userId, targetId, targetType, false);
        } catch (BusinessException be) {
            throw be;//重新抛出
        } catch (Exception e) {
            log.error("[redis] 执行点赞操作异常 userId: {}, target: {}:{}. Error: {}",
                    userId, targetType, targetId, e.getMessage(), e);
            // Consider if a rollback or compensation is needed in Redis if script partially failed (though Lua is atomic)
            throw new SystemException(ResultCode.FAILED_OPERATION_REDIS_SCRIPT, "Like operation failed.");
        }
    }

    /**
     * 异步执行数据库操作
     */
    @Async(DB_PERSISTENCE_EXECUTOR) // Make sure Spring's AOP proxy can intercept this call for async execution
    @Transactional // For database operation
    public void persistLikeStateAsync(Long userId, Long targetId, String targetType, boolean isLiked) {
        try {
            //1. 再次检查数据库的记录
            Likes existingLike = isLikes(userId, targetId, targetType); ;
            if (isLiked) {
                //2. 插入数据库
                if (existingLike == null) {
                    Likes like = new Likes();
                    like.setUserId(userId);
                    like.setTargetId(targetId);
                    like.setTargetType(targetType);
                    like.setCreateTime(LocalDateTime.now());
                    likesMapper.insert(like);
                    log.debug("DB: 插入点赞成功: {}, target: {}:{}", userId, targetType, targetId);
                    // 3. 更新 article 或 reply 的 like_count
                    if(updateLikeCountInDb(targetId, targetType, 1) != 1){
                        throw new SystemException(ResultCode.FAILED_CHANGE_LIKE);
                    }
                } else {
                    // Optionally update timestamp or status if already exists but was (e.g.) soft-deleted
                    log.debug("DB: 点赞记录已经存在 userId: {}, target: {}:{}", userId, targetType, targetId);
                }
            } else {
                if(existingLike != null){
                    int deletedRows = likesMapper.deleteById(existingLike.getId());
                    if (deletedRows > 0) {
                        log.debug("DB: 点赞记录删除成功 userId: {}, target: {}:{}", userId, targetType, targetId);
                    } else {
                        log.error("DB: 点赞记录删除失败 userId: {}, target: {}:{}", userId, targetType, targetId);
                        throw new SystemException(ResultCode.FAILED_CHANGE_LIKE);
                    }
                }
                // 3. 更新 article 或 reply 的 like_count
                if(updateLikeCountInDb(targetId, targetType, -1) != 1){
                    throw new SystemException(ResultCode.FAILED_CHANGE_LIKE);
                }
            }
        } catch (Exception e) {
            // Critical: Log and monitor these failures. Consider a retry mechanism or dead-letter queue.
            log.error("DB: 数据库 点赞/取消赞 执行出错 userId: {}, target: {}:{}, isLiked: {}. Error: {}",
                    userId, targetType, targetId, isLiked, e.getMessage(), e);
            // Do NOT throw an exception that would roll back the Redis operation if the primary goal is Redis speed.
            // If DB persistence is critical and synchronous, then this async approach isn't suitable.
        }
    }



    @Override
    public boolean checkLikeStatus(Long userId, Long targetId, String targetType) {
        // 1. 查询redis，确认是否已点赞
        String userLikesSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, targetId);
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(userLikesSetKey, userId.toString());
        if (isMember != null) { // isMember can be null if Redis connection issue, though unlikely here
            return isMember;
        }
        // 2. redis查询失败，查询数据库
        Likes existingLike = getLikeFromDb(userId, targetId, targetType);
        return existingLike != null;
    }

    private Likes getLikeFromDb(Long userId, Long targetId, String targetType){ // Renamed for clarity
        return likesMapper.selectOne(new LambdaQueryWrapper<Likes>().eq(Likes::getUserId, userId)
                .eq(Likes::getTargetId, targetId).eq(Likes::getTargetType, targetType));
    }

    private int updateLikeCountInDb(Long targetId, String targetType, int increment) {
        if (TARGET_TYPE_ARTICLE.equals(targetType)) {
            return  articleService.updateLikeCount(targetId, increment);
        } else if (TARGET_TYPE_REPLY.equals(targetType)) {
            return articleReplyService.updateLikeCount(targetId, increment);
        } else {
            log.error("取消/新增点赞失败，targetId: {}, targetType: {}",targetId, targetType);
            throw new SystemException(ResultCode.FAILED_CHANGE_LIKE);
        }
    }
}