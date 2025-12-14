package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.common.config.RabbitMQConfig;
import com.doublez.kc_forum.common.event.ArticleLikeEvent;
import com.doublez.kc_forum.mapper.LikesMapper;
import com.doublez.kc_forum.model.Likes;
import com.doublez.kc_forum.service.ILikesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LikesServiceImpl implements ILikesService {

    private final LikesMapper likesMapper;
    private final RedisScript<List> likeScript; // 假设List中是Long类型
    private final RedisScript<List> unlikeScript; // 假设List中是Long类型
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    // 文章/回复 Hash中likeCount字段的名称 (统一管理)
    private static final String LIKE_COUNT_FIELD = "likeCount";
    // 用于在Redis Set中标记一个目标没有点赞记录的占位符（防止缓存穿透）
    private static final String EMPTY_LIKE_CACHE_STRING = "__EMPTY_LIKES_PLACEHOLDER__"; // 使用更特殊的标记

    // 目标类型常量
    private static final String TARGET_TYPE_ARTICLE = "article";
    private static final String TARGET_TYPE_REPLY = "reply";

    @Autowired
    public LikesServiceImpl(StringRedisTemplate stringRedisTemplate,
                            @Qualifier("likeScript") RedisScript<List> likeScript, // 确保Bean名称正确
                            @Qualifier("unlikeScript") RedisScript<List> unlikeScript, // 确保Bean名称正确
                            LikesMapper likesMapper,
                            RabbitTemplate rabbitTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.likeScript = likeScript;
        this.unlikeScript = unlikeScript;
        this.likesMapper = likesMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 用户点赞目标。
     *
     * @param userId     用户ID
     * @param targetId   目标ID (文章ID或回复ID)
     * @param targetType 目标类型 ("article" 或 "reply")
     */
    @Override
    public void like(Long userId, Long targetId, String targetType) {
        String targetHashKey = getTargetHashKeyAndValidateType(targetId, targetType);
        String likersSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, targetId);

        try {
            List<Long> results = stringRedisTemplate.execute(
                    likeScript,
                    List.of(likersSetKey, targetHashKey), // KEYS
                    userId.toString(), LIKE_COUNT_FIELD, EMPTY_LIKE_CACHE_STRING // ARGV
            );
            handleScriptResult(results, userId, targetId, targetType, "点赞", true, targetHashKey);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[Redis] 执行点赞脚本时发生异常, likersSetKey:{}, targetHashKey:{}, userId:{}, target:{}:{}, 错误:{}",
                    likersSetKey, targetHashKey, userId, targetType, targetId, e.getMessage(), e);
            throw new SystemException(ResultCode.FAILED_OPERATION_REDIS_SCRIPT, "Redis点赞操作失败: " + e.getMessage());
        }
    }

    /**
     * 用户取消点赞目标。
     *
     * @param userId     用户ID
     * @param targetId   目标ID
     * @param targetType 目标类型
     */
    @Override
    public void unlike(Long userId, Long targetId, String targetType) {
        String targetHashKey = getTargetHashKeyAndValidateType(targetId, targetType);
        String likersSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, targetId);

        try {
            List<Long> results = stringRedisTemplate.execute(
                    unlikeScript,
                    List.of(likersSetKey, targetHashKey), // KEYS
                    userId.toString(), LIKE_COUNT_FIELD, EMPTY_LIKE_CACHE_STRING // ARGV
            );
            handleScriptResult(results, userId, targetId, targetType, "取消点赞", false, targetHashKey);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[Redis] 执行取消点赞脚本时发生异常, likersSetKey:{}, targetHashKey:{}, userId:{}, target:{}:{}, 错误:{}",
                    likersSetKey, targetHashKey, userId, targetType, targetId, e.getMessage(), e);
            throw new SystemException(ResultCode.FAILED_OPERATION_REDIS_SCRIPT, "Redis取消点赞操作失败: " + e.getMessage());
        }
    }

    /**
     * 根据目标类型和ID获取其在Redis中存储详情(包括点赞数)的Hash Key，并校验类型。
     *
     * @param targetId   目标ID
     * @param targetType 目标类型
     * @return Redis Hash Key
     * @throws BusinessException 如果目标类型不支持
     */
    private String getTargetHashKeyAndValidateType(Long targetId, String targetType) {
        String hashKey;
        if (TARGET_TYPE_ARTICLE.equalsIgnoreCase(targetType)) {
            hashKey = RedisKeyUtil.getArticleKey(targetId);
        } else if (TARGET_TYPE_REPLY.equalsIgnoreCase(targetType)) {
            hashKey = RedisKeyUtil.getArticleReplyKey(targetId);
        } else {
            log.warn("不支持的点赞目标类型: {}", targetType);
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE, "不支持的目标类型");
        }
        return hashKey;
    }

    /**
     * 处理Lua脚本返回的结果。
     *
     * @param results         Lua脚本返回的List，通常包含[操作状态, 当前/新的计数值]
     * @param userId          用户ID
     * @param targetId        目标ID
     * @param targetType      目标类型
     * @param operation       操作名称（用于日志）
     * @param isLikeOperation 是点赞操作(true)还是取消点赞操作(false)
     * @param targetHashKey   目标在Redis中的Hash Key (用于可能的日志或调试)
     */
    private void handleScriptResult(List<Long> results, Long userId, Long targetId, String targetType, String operation, boolean isLikeOperation, String targetHashKey) {
        if (results == null || results.size() < 2) {
            log.error("Redis {} 脚本执行结果不符合预期 (大小或为null), userId:{}, target:{}:{}, targetHashKey:{}, 返回结果:{}",
                    operation, userId, targetType, targetId, targetHashKey, results);
            throw new SystemException(ResultCode.FAILED_OPERATION_REDIS_SCRIPT, "Redis脚本执行结果异常");
        }

        long operationStatus = results.get(0); // Lua脚本返回的实际操作状态 (1=成功改变, 0=未改变或已是目标状态)

        if (operationStatus == 0) { // 状态未发生变化 (例如：重复点赞，或取消一个未点赞的内容)
            // 用户操作无效，直接抛出业务异常。
            // newOrCurrentCountFromRedis 是 Redis 中当前的点赞数。
            String messageTemplate = isLikeOperation ? "您已点赞过该内容" : "您未点赞过该内容，无法取消";
            log.warn("用户 {} 对目标 {}:{} 进行 {} 操作失败 (状态未改变). 原因: {}",
                    userId, targetType, targetId, operation, messageTemplate);
            throw new BusinessException(ResultCode.FAILED_CHANGE_LIKE, messageTemplate);
        }

        // 操作成功 (operationStatus == 1)，状态发生变更
        log.info("用户 {} 成功对目标进行 {} 操作 {}:{}",
                userId, operation, targetType, targetId);

        // 发送消息到RabbitMQ
        ArticleLikeEvent event = new ArticleLikeEvent(userId, targetId, targetType, System.currentTimeMillis(), isLikeOperation);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.LIKE_ROUTING_KEY, event);
        log.info("已发送点赞事件到RabbitMQ: {}", event);
    }

    /**
     * 检查用户对目标的点赞状态。
     *
     * @param userId     用户ID
     * @param targetId   目标ID
     * @param targetType 目标类型
     * @return true 如果用户已点赞，false 则未点赞
     */
    @Override
    public boolean checkLikeStatus(Long userId, Long targetId, String targetType) {
        String likersSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, targetId);

        // 1. 直接查询用户是否是Set的成员
        Boolean isUserMember = stringRedisTemplate.opsForSet().isMember(likersSetKey, userId.toString());

        if (isUserMember == null) {
            log.error("Redis SISMEMBER 查询异常, key:{}", likersSetKey);
            // 正常来说没查到返回false，保险起见记录一下
            throw new SystemException(ResultCode.FAILED_OPERATION_REDIS, "查询点赞状态时Redis操作失败");
        }

        if (isUserMember) {
            // 用户ID在集合中，说明已点赞。
            return true;
        }

        // 用户ID不在集合中 (isUserMember == false)。
        // 此时需要判断：是目标确实无此用户点赞，还是缓存未命中/key不存在。
        // 通过检查Set的大小来辅助判断。如果key不存在，size()通常返回0。
        Long setSize = stringRedisTemplate.opsForSet().size(likersSetKey);

        if (setSize == null) { // 理论上 size() 对于存在的 StringRedisTemplate 不会返回 null
            log.warn("Redis SCARD (size) 查询返回 null, key:{}. 视为缓存未命中.", likersSetKey);
            // 视为缓存未命中，从数据库加载
            return getLikeFromDbAndCache(likersSetKey, userId, targetId, targetType);
        }

        if (setSize == 0) {
            // Set大小为0，意味着Redis中没有此目标的任何点赞者记录（也没有空缓存标记）。
            // 这通常表示缓存首次加载、缓存失效或Redis数据丢失。
            log.info("Redis Set (key:{}) 大小为0, 视为缓存未命中或失效, 从数据库加载并缓存.", likersSetKey);
            return getLikeFromDbAndCache(likersSetKey, userId, targetId, targetType);
        }

        // isUserMember is false, 且 setSize > 0.
        // 这意味着Set中存在成员，但不是当前查询的userId。
        // 这些成员可能是其他真实点赞者，或者仅仅是 EMPTY_LIKE_CACHE_STRING。
        // 无论哪种情况，当前用户都是未点赞状态。
        return false;
    }

    /**
     * 从数据库加载指定目标的所有点赞用户ID，并将其缓存到Redis Set中。
     * 如果数据库没有点赞记录，则缓存一个空标记。
     * 同时，会用数据库的实际点赞数校正Redis Hash中的点赞总数字段。
     *
     * @param likersSetKey Redis中存储点赞用户集合的Key
     * @param targetId     目标ID
     * @param targetType   目标类型
     * @return 从数据库中查询到的用户ID列表 (字符串形式)
     */
    private String[] loadAndCacheLikesFromDbInternal(String likersSetKey, Long targetId, String targetType) {
        log.info("缓存重建: 开始从数据库加载点赞数据, key:{}, targetId:{}, targetType:{}",
                likersSetKey, targetId, targetType);

        List<Likes> likesFromDb = likesMapper.selectList(new LambdaQueryWrapper<Likes>()
                .eq(Likes::getTargetId, targetId)
                .eq(Likes::getTargetType, targetType));

        String[] userIdsFromDb = likesFromDb.stream()
                .map(like -> like.getUserId().toString())
                .toArray(String[]::new);

        // 在更新缓存前，先清除旧的Set内容，确保状态干净
        stringRedisTemplate.delete(likersSetKey);

        String targetHashKey = getTargetHashKeyAndValidateType(targetId, targetType);

        if (userIdsFromDb.length > 0) {
            stringRedisTemplate.opsForSet().add(likersSetKey, userIdsFromDb);
            // 校正Redis Hash中的点赞总数
            stringRedisTemplate.opsForHash().put(targetHashKey, LIKE_COUNT_FIELD, String.valueOf(userIdsFromDb.length));
            log.info("缓存重建: 成功从数据库加载并缓存点赞数据, key:{}, 缓存用户数:{}, Hash计数已校正为:{}",
                    likersSetKey, userIdsFromDb.length, userIdsFromDb.length);
        } else {
            // 数据库中没有点赞记录，缓存空标记以防穿透
            stringRedisTemplate.opsForSet().add(likersSetKey, EMPTY_LIKE_CACHE_STRING);
            // 同时确保Redis Hash中的点赞总数为0
            stringRedisTemplate.opsForHash().put(targetHashKey, LIKE_COUNT_FIELD, "0");
            log.info("缓存重建: 数据库中未查询到点赞数据, key:{}, 进行空值缓存(添加 '{}'), Hash计数已校正为0",
                    likersSetKey, EMPTY_LIKE_CACHE_STRING);
        }

        // 为新建的缓存（无论是真实数据还是空标记）设置一个合理的过期时间
         stringRedisTemplate.expire(likersSetKey, 1, TimeUnit.DAYS);

        return userIdsFromDb;
    }

    /**
     * 当缓存未命中或失效时，从数据库获取指定用户的点赞状态，并确保相关点赞数据已缓存。
     *
     * @param likersSetKey Redis中存储点赞用户集合的Key
     * @param userId       用户ID
     * @param targetId     目标ID
     * @param targetType   目标类型
     * @return 用户是否点赞
     */
    private boolean getLikeFromDbAndCache(String likersSetKey, Long userId, Long targetId, String targetType) {
        // 调用内部方法从数据库加载并缓存数据
        String[] cachedUserIds = loadAndCacheLikesFromDbInternal(likersSetKey, targetId, targetType);

        // 检查当前用户是否在从数据库加载的列表中
        for (String cachedUserId : cachedUserIds) {
            if (cachedUserId.equals(userId.toString())) {
                return true; // 用户在从数据库加载的数据中，已点赞
            }
        }
        return false; // 用户不在从数据库加载的数据中，未点赞
    }
}