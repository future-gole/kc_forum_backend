package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.mapper.ArticleReplyMapper;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.ArticleReply;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.BeforeEach;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

@SpringBootTest(properties = "spring.data.redis.repositories.enabled=false")
class ArticleReplyServiceImplTest {

    @Autowired
    private ArticleReplyServiceImpl articleReplyService;

    @MockBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @MockBean
    private ArticleReplyMapper articleReplyMapper;

    @MockBean
    private UserServiceImpl userServiceImpl;

    @MockBean
    private ArticleMapper articleMapper;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;



    @MockBean
    private RedisAsyncPopulationService redisAsync;

    @MockBean
    private DBAsyncPopulationService dbAsync;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Article.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleReply.class);
    }

    @Test
    void createArticleReply_Success() {
        // Arrange
        ArticleReplyAddRequest request = new ArticleReplyAddRequest();
        request.setPostUserId(1L);
        request.setArticleId(1L);
        request.setContent("Test Reply");

        Article mockArticle = new Article();
        mockArticle.setId(1L);
        mockArticle.setState((byte) 0);
        mockArticle.setDeleteState((byte) 0);
        mockArticle.setReplyCount(0);

        when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockArticle);
        when(articleReplyMapper.insert(any(ArticleReply.class))).thenReturn(1);

        HashOperations hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // Act
        articleReplyService.createArticleReply(request);

        // Assert
        verify(articleMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(articleReplyMapper).insert(any(ArticleReply.class));
        // Verify async calls if any (need to check implementation details, but basic flow is covered)
    }

    @Test
    void createArticleReply_ArticleNotFound() {
        // Arrange
        ArticleReplyAddRequest request = new ArticleReplyAddRequest();
        request.setPostUserId(1L);
        request.setArticleId(1L);
        request.setContent("Test Reply");

        when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> articleReplyService.createArticleReply(request));
        assertEquals(ResultCode.FAILED_ARTICLE_NOT_EXISTS.getCode(), exception.getResultCode().getCode());
    }

    @Test
    void createArticleReply_ArticleBanned() {
        // Arrange
        ArticleReplyAddRequest request = new ArticleReplyAddRequest();
        request.setPostUserId(1L);
        request.setArticleId(1L);
        request.setContent("Test Reply");

        Article mockArticle = new Article();
        mockArticle.setId(1L);
        mockArticle.setState((byte) 1); // Banned
        mockArticle.setDeleteState((byte) 0);

        when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockArticle);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> articleReplyService.createArticleReply(request));
        assertEquals(ResultCode.FAILED_ARTICLE_BANNED.getCode(), exception.getResultCode().getCode());
    }
}
