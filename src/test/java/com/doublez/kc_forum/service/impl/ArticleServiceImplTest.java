package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.request.UpdateArticleRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.model.Article;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.doublez.kc_forum.service.impl.ArticleServiceImpl.FIELD_TITLE;
import static com.doublez.kc_forum.service.impl.ArticleServiceImpl.FIELD_UPDATE_TIME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
@SpringBootTest(properties = "spring.data.redis.repositories.enabled=false")
class ArticleServiceImplTest {
    @Autowired
    ArticleServiceImpl articleService;
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean(name = "redisTemplate")
    private RedisTemplate redisTemplate;
    @MockBean(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;
    @MockBean
    private HashOperations hashOperations;
    @MockBean
    private ZSetOperations zSetOperations;
    @MockBean
    private org.springframework.data.redis.core.ValueOperations valueOperations;
    @Autowired
    private ArticleMapper articleMapper;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createArticle() {
        Article article = new Article();
        article.setTitle("title");
        article.setContent("content");
        article.setUserId(1L);
        article.setBoardId(1L);
        articleService.createArticle(article);
        System.out.println("更新成功");
    }

    @Test
    void getAllArticlesByBoardId() {
        List<ArticleMetaCacheDTO> articles = articleService.getAllArticlesByBoardId(1L);
        System.out.println(articles.toString());
    }

    @Test
    void getUserId() {
        System.out.println(articleService.getUserId(1L));
    }

    @Test
    void updateArticle() {
        redisTemplate.opsForHash().put(RedisKeyUtil.getArticleKey(60L),"title", "121212");
        //2.2 如果content存在那么删除，下次需要的时候再缓存
        Boolean delete = stringRedisTemplate.delete(RedisKeyUtil.getArticleContentKey(60L));
        if(!delete){
            log.error("从redis中删除文章 {} 内容失败",60L);
        }
        log.info("帖子 redis 更新成功，articleId:{}",60L);
    }

    @Test
    void updateArticle1() {
        UpdateArticleRequest updateArticleRequest = new UpdateArticleRequest();
        updateArticleRequest.setTitle("12212121");
        updateArticleRequest.setContent("content121");
        updateArticleRequest.setId(60L);
        LocalDateTime now = LocalDateTime.now();
        //1. 更新数据库
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getDeleteState, Article::getState)
                .eq(Article::getId,updateArticleRequest.getId()));
        if( article.getState() == 1  ){
            log.warn("帖子被禁言, id:{}",updateArticleRequest.getId());
            throw new BusinessException(ResultCode.FAILED_ARTICLE_BANNED);
        }else if(  article.getDeleteState() == 1 ){
            log.warn("帖子被删除, id:{}",updateArticleRequest.getId());
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }

        int update = articleMapper.update(new LambdaUpdateWrapper<Article>()
                .set(Article::getTitle, updateArticleRequest.getTitle())
                .set(Article::getContent, updateArticleRequest.getContent())

                .set(Article::getUpdateTime, now)
                .eq(Article::getId, updateArticleRequest.getId()));
        if(update != 1){
            log.error("帖子更新失败,articleId:{}",updateArticleRequest.getId());
            throw new SystemException(ResultCode.FAILED_UPDATE_ARTICLE);
        }
        log.info("帖子DB更新成功：articleId{}",updateArticleRequest.getId());

        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_TITLE, updateArticleRequest.getTitle());
        updates.put(FIELD_UPDATE_TIME,now);
        redisTemplate.opsForHash().putAll(RedisKeyUtil.getArticleKey(updateArticleRequest.getId()),updates);
        //2.2 如果content存在那么删除，下次需要的时候再缓存
        Boolean delete = stringRedisTemplate.delete(RedisKeyUtil.getArticleContentKey(updateArticleRequest.getId()));
        if(!delete){
            log.error("从redis中删除文章 {} 内容失败",updateArticleRequest.getId());
        }
        log.info("帖子 redis 更新成功，articleId:{}",updateArticleRequest.getId());
    }
}