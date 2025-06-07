package com.doublez.kc_forum.service.impl;

import com.doublez.kc_forum.common.pojo.response.UserArticleResponse;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.User;
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

import static com.doublez.kc_forum.service.impl.ArticleServiceImpl.*;
import static com.doublez.kc_forum.service.impl.LikesServiceImpl.DB_PERSISTENCE_EXECUTOR;
@Slf4j
@Service
public class RedisAsyncPopulationService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Async(DB_PERSISTENCE_EXECUTOR)
    public void boardZsetFromDBToRedis(String boardArticlesZSetKey, List<Article> articlesFromDb) {
        stringRedisTemplate.opsForZSet().add(boardArticlesZSetKey,
                articlesFromDb.stream().map(
                                article ->
                                        new DefaultTypedTuple<>(article.getId().toString(),(double) article.getCreateTime().toEpochSecond(ZoneOffset.UTC)))
                        .collect(Collectors.toSet()));
    }

    /**
     * 将文章对象的主要信息和计数缓存到 Redis Hash，内容单独缓存。
     * @param articles 文章对象
     */
    @Async(DB_PERSISTENCE_EXECUTOR)
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
                    log.info("文章内容已成功缓存，id:{}",article.getId());
                }
                return null;
            }
        });
    }

    /**
     * 缓存用户
     * @param uar 用户实体
     */
    @Async(DB_PERSISTENCE_EXECUTOR)
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
    @Async(DB_PERSISTENCE_EXECUTOR)
    public void incrVisit(Long articleId) {
        String articleKey = RedisKeyUtil.getArticleKey(articleId);
        stringRedisTemplate.opsForHash().increment(articleKey, FIELD_VISIT_COUNT, 1);
    }
}
