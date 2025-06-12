package com.doublez.kc_forum.service.impl;

import com.doublez.kc_forum.common.pojo.response.ArticleReplyMetaCacheDTO;
import com.doublez.kc_forum.common.pojo.response.UserArticleResponse;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.ArticleReply;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.doublez.kc_forum.common.config.AsyncConfig.REDIS_PERSISTENCE_EXECUTOR;
import static com.doublez.kc_forum.service.impl.ArticleServiceImpl.*;
@Slf4j
@Service
public class RedisAsyncPopulationService {

    //回复贴
    private static final String REPLY_FIELD_ID = "id";
    private static final String REPLY_FIELD_ARTICLE_ID = "articleId";
    private static final String REPLY_FIELD_POST_USER_ID = "postUserId";
    private static final String REPLY_FIELD_REPLY_ID = "replyId";
    private static final String REPLY_FIELD_REPLY_USER_ID = "replyUserId";
    private static final String REPLY_FIELD_CONTENT = "content";
    private static final String REPLY_FIELD_CREATE_TIME = "createTime";
    private static final String REPLY_FIELD_LIKE_COUNT = "likeCount";
    private static final String REPLY_FIELD_CHILDREN_COUNT = "childrenCount";//该回复下直接子回复的数量

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Autowired
    private ArticleReply articleReply;

    @Async(REDIS_PERSISTENCE_EXECUTOR)
    public void boardZsetFromDBToRedis(String boardArticlesZSetKey, List<Article> articlesFromDb) {
        stringRedisTemplate.opsForZSet().add(boardArticlesZSetKey,
                articlesFromDb.stream().map(
                                article ->
                                        new DefaultTypedTuple<>(article.getId().toString(),(double) article.getCreateTime().toEpochSecond(ZoneOffset.UTC)))
                        .collect(Collectors.toSet()));
    }

    @Async(REDIS_PERSISTENCE_EXECUTOR)
    public void articleZsetFromDBToRedis(String topRepliesKey, List<ArticleReply> articleRepliesFromDb) {
        stringRedisTemplate.opsForZSet().add(topRepliesKey,
                articleRepliesFromDb.stream().map(
                                reply ->
                                        new DefaultTypedTuple<>(reply.getId().toString(),(double) reply.getCreateTime().toEpochSecond(ZoneOffset.UTC)))
                        .collect(Collectors.toSet()));
    }

    @Async(REDIS_PERSISTENCE_EXECUTOR)
    public void replyZsetFromDBToRedis(String replyRepliesKey, List<ArticleReply> articleRepliesFromDb) {
        stringRedisTemplate.opsForZSet().add(replyRepliesKey,
                articleRepliesFromDb.stream().map(
                                reply ->
                                        new DefaultTypedTuple<>(reply.getId().toString(),(double) reply.getCreateTime().toEpochSecond(ZoneOffset.UTC)))
                        .collect(Collectors.toSet()));
    }

    /**
     * 将文章对象的主要信息和计数缓存到 Redis Hash。
     * @param articles 文章对象
     */
    @Async(REDIS_PERSISTENCE_EXECUTOR)
    public void cacheArticleList(List<Article> articles) {
        if (articles == null) return;

        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations redisOperations) throws DataAccessException {
                for (Article article : articles) {
                    if(article == null || article.getId() == null){
                        log.warn("文章数据为null");
                        continue;
                    }
                    String articleKey = RedisKeyUtil.getArticleKey(article.getId());
                    Map<String, Object> articleMap = new HashMap<>();
                    articleMap.put(FIELD_ID, article.getId().toString());
                    articleMap.put(FIELD_BOARD_ID, article.getBoardId().toString());
                    articleMap.put(FIELD_USER_ID, article.getUserId().toString());
                    articleMap.put(FIELD_TITLE, article.getTitle());
                    articleMap.put(FIELD_IS_TOP, article.getIsTop() != null ? article.getIsTop().toString() : "0");
                    // 使用ISO-8601格式的日期时间字符串，RedisTemplate中的Jackson应该能正确处理JavaTimeModule
                    articleMap.put(FIELD_CREATE_TIME, article.getCreateTime()); // Jackson会序列化
                    articleMap.put(FIELD_UPDATE_TIME, article.getUpdateTime()); // Jackson会序列化
                    articleMap.put(FIELD_REPLY_COUNT, article.getReplyCount() != null ? article.getReplyCount() : 0);
                    articleMap.put(FIELD_LIKE_COUNT, article.getLikeCount() != null ? article.getLikeCount(): 0);
                    articleMap.put(FIELD_VISIT_COUNT, article.getVisitCount() != null ? article.getVisitCount() : 0);

                    redisTemplate.opsForHash().putAll(articleKey, articleMap); // 存入Hash

                    // 单独缓存文章内容
                    if (StringUtils.hasText(article.getContent())) {
                        String contentKey = RedisKeyUtil.getArticleContentKey(article.getId());
                        stringRedisTemplate.opsForValue().set(contentKey, article.getContent(),1, TimeUnit.HOURS);
                    }
                    log.info("文章元数据已成功缓存，id:{}",article.getId());
                }
                return null;
            }
        });
    }

    /**
     * 将文章回复贴的信息和计数缓存到 Redis Hash。
     * @param replies 文章对象
     */
    @Async(REDIS_PERSISTENCE_EXECUTOR)
    public void cacheArticleReplyList(List<ArticleReplyMetaCacheDTO> replies) {
        if (replies == null) return;

        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations redisOperations) throws DataAccessException {
                for (ArticleReplyMetaCacheDTO reply : replies) {
                    if(reply == null || reply.getId() == null){
                        log.warn("回复贴数据为null");
                        continue;
                    }
                    String articleKey = RedisKeyUtil.getArticleReplyKey(reply.getId());
                    Map<String, Object> articleReplyMap = new HashMap<>();
                    articleReplyMap.put(REPLY_FIELD_ID, reply.getId().toString());
                    articleReplyMap.put(REPLY_FIELD_ARTICLE_ID, reply.getArticleId().toString());
                    articleReplyMap.put(REPLY_FIELD_POST_USER_ID, reply.getPostUserId().toString());
                    articleReplyMap.put(REPLY_FIELD_REPLY_ID, reply.getReplyId() != null ? reply.getReplyId().toString() : "0");
                    articleReplyMap.put(REPLY_FIELD_REPLY_USER_ID, reply.getReplyUserId().toString());
                    articleReplyMap.put(REPLY_FIELD_CONTENT, reply.getContent());
                    articleReplyMap.put(REPLY_FIELD_LIKE_COUNT, reply.getLikeCount() != null ? reply.getLikeCount() : 0);
                    // 使用ISO-8601格式的日期时间字符串，RedisTemplate中的Jackson应该能正确处理JavaTimeModule
                    articleReplyMap.put(REPLY_FIELD_CREATE_TIME, reply.getCreateTime()); // Jackson会序列化
                    articleReplyMap.put(REPLY_FIELD_CHILDREN_COUNT,reply.getChildrenCount() != null ? reply.getChildrenCount() : 0);
                    redisTemplate.opsForHash().putAll(articleKey, articleReplyMap); // 存入Hash

                    log.info("回复数据已成功缓存，id:{}",reply.getId());
                }
                return null;
            }
        });
    }

    /**
     * 缓存用户
     * @param uar 用户实体
     */
    @Async(REDIS_PERSISTENCE_EXECUTOR)
    public void cacheUser(UserArticleResponse uar ){
        try {
            stringRedisTemplate.opsForValue().set(RedisKeyUtil.getUserResponseKey(uar.getId()), objectMapper.writeValueAsString(uar), 1, TimeUnit.HOURS); // 设置TTL
        } catch (JsonProcessingException e) { log.error("序列化用户 {} 失败", uar.getId(), e); }
        log.info("从数据库缓存了用户 {}", uar.getId());
    }

    /**
     * 增加redis中的文章访问数量
     * @param articleId 文章id
     */
    @Async(REDIS_PERSISTENCE_EXECUTOR)
    public void incrVisit(Long articleId) {
        String articleKey = RedisKeyUtil.getArticleKey(articleId);
        stringRedisTemplate.opsForHash().increment(articleKey, FIELD_VISIT_COUNT, 1);
    }
}
