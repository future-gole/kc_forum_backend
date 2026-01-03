package com.doublez.kc_forum.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", 3600000L);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createRefreshToken_Success() {
        String token = "test-token";
        
        String result = refreshTokenService.createRefreshToken(token);

        assertEquals(token, result);
        verify(valueOperations).set(anyString(), eq(""), eq(3600000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void validateRefreshToken_Success() {
        String token = "test-token";
        when(valueOperations.get(anyString())).thenReturn("");

        Optional<String> result = refreshTokenService.validateRefreshToken(token);

        assertTrue(result.isPresent());
    }

    @Test
    void validateRefreshToken_NotFound() {
        String token = "test-token";
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<String> result = refreshTokenService.validateRefreshToken(token);

        assertFalse(result.isPresent());
    }

    @Test
    void deleteRefreshToken_Success() {
        String token = "test-token";
        
        refreshTokenService.deleteRefreshToken(token);

        verify(stringRedisTemplate).delete(anyString());
    }
}
