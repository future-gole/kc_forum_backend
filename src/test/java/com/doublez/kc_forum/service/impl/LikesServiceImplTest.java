package com.doublez.kc_forum.service.impl;

import com.doublez.kc_forum.common.config.RabbitMQConfig;
import com.doublez.kc_forum.common.event.ArticleLikeEvent;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.mapper.LikesMapper;
import com.doublez.kc_forum.model.Likes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikesServiceImplTest {

    @Mock
    private LikesMapper likesMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisScript<List> likeScript;

    @Mock
    private RedisScript<List> unlikeScript;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private LikesServiceImpl likesService;

    private final Long userId = 1L;
    private final Long articleId = 100L;
    private final String targetType = "article";

    @BeforeEach
    void setUp() {
        // Manually inject mocks to avoid ambiguity
        likesService = new LikesServiceImpl(stringRedisTemplate, likeScript, unlikeScript, likesMapper, rabbitTemplate);
        
        // Lenient stubbing for operations that might not be called in all tests
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void like_Success() {
        // Mock Redis script execution result: [1, 10] (status=1 success, count=10)
        List<Long> scriptResult = List.of(1L, 10L);
        when(stringRedisTemplate.execute(
                eq(likeScript),
                anyList(),
                any(), any(), any()
        )).thenReturn(scriptResult);

        likesService.like(userId, articleId, targetType);

        // Verify RabbitMQ message sent
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.LIKE_ROUTING_KEY),
                any(ArticleLikeEvent.class)
        );
    }

    @Test
    void like_AlreadyLiked_ThrowsException() {
        // Mock Redis script execution result: [0, 10] (status=0 no change)
        List<Long> scriptResult = List.of(0L, 10L);
        when(stringRedisTemplate.execute(
                eq(likeScript),
                anyList(),
                any(), any(), any()
        )).thenReturn(scriptResult);

        assertThrows(BusinessException.class, () -> likesService.like(userId, articleId, targetType));

        // Verify RabbitMQ message NOT sent
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void unlike_Success() {
        // Mock Redis script execution result: [1, 9] (status=1 success, count=9)
        List<Long> scriptResult = List.of(1L, 9L);
        when(stringRedisTemplate.execute(
                eq(unlikeScript),
                anyList(),
                any(), any(), any()
        )).thenReturn(scriptResult);

        likesService.unlike(userId, articleId, targetType);

        // Verify RabbitMQ message sent
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.LIKE_ROUTING_KEY),
                any(ArticleLikeEvent.class)
        );
    }

    @Test
    void unlike_NotLiked_ThrowsException() {
        // Mock Redis script execution result: [0, 10] (status=0 no change)
        List<Long> scriptResult = List.of(0L, 10L);
        when(stringRedisTemplate.execute(
                eq(unlikeScript),
                anyList(),
                any(), any(), any()
        )).thenReturn(scriptResult);

        assertThrows(BusinessException.class, () -> likesService.unlike(userId, articleId, targetType));
    }

    @Test
    void checkLikeStatus_CacheHit_Liked() {
        String likersSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, articleId);
        when(setOperations.isMember(likersSetKey, userId.toString())).thenReturn(true);

        boolean result = likesService.checkLikeStatus(userId, articleId, targetType);

        assertTrue(result);
    }

    @Test
    void checkLikeStatus_CacheHit_NotLiked() {
        String likersSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, articleId);
        when(setOperations.isMember(likersSetKey, userId.toString())).thenReturn(false);
        when(setOperations.size(likersSetKey)).thenReturn(5L); // Set exists and has members

        boolean result = likesService.checkLikeStatus(userId, articleId, targetType);

        assertFalse(result);
    }

    @Test
    void checkLikeStatus_CacheMiss_LoadFromDb_Liked() {
        String likersSetKey = RedisKeyUtil.getUserLikesTargetSetKey(targetType, articleId);
        when(setOperations.isMember(likersSetKey, userId.toString())).thenReturn(false);
        when(setOperations.size(likersSetKey)).thenReturn(0L); // Set empty/missing

        // Mock DB return
        Likes like = new Likes();
        like.setUserId(userId);
        like.setTargetId(articleId);
        like.setTargetType(targetType);
        when(likesMapper.selectList(any())).thenReturn(List.of(like));

        boolean result = likesService.checkLikeStatus(userId, articleId, targetType);

        assertTrue(result);
        // Verify cache update
        verify(setOperations).add(eq(likersSetKey), any(String[].class));
    }
}
