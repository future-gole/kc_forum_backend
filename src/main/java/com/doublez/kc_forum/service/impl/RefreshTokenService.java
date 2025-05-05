package com.doublez.kc_forum.service.impl;

import com.doublez.kc_forum.common.utiles.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class RefreshTokenService {

    @Value("${jwt.refresh-token.expiration-ms}")
    private Long refreshTokenDurationMs;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refreshtoken:";

    /**
     * 为指定用户创建并存储一个新的 Refresh Token 到 Redis。
     * 这里我们使用 JWT 作为 Refresh Token，但只验证其在 Redis 中的存在性。
     *
     * @param refreshTokenString 关联的用户UUID
     * @return 生成的 Refresh Token 字符串
     */
    public String createRefreshToken(String refreshTokenString) {
        Map<String, Object> claims = new HashMap<>();
        String redisKey = REFRESH_TOKEN_PREFIX + refreshTokenString;
        stringRedisTemplate.opsForValue().set(redisKey, "", refreshTokenDurationMs, TimeUnit.MILLISECONDS);
        return refreshTokenString;
    }

    /**
     * 验证 Refresh Token 是否在 Redis 中存在且有效。
     *
     * @param token 要验证的 Refresh Token 字符串
     * @return 如果有效，返回包含用户名的 Optional；否则返回空 Optional。
     */
    public Optional<String> validateRefreshToken(String token) {
        String redisKey = REFRESH_TOKEN_PREFIX + token;
        String userCookie = stringRedisTemplate.opsForValue().get(redisKey);
        return Optional.ofNullable(userCookie);
    }
    /**
     * 从 Redis 中删除指定的 Refresh Token。
     * 用于登出或 Refresh Token 旋转。
     *
     * @param token 要删除的 Refresh Token 字符串
     */
    public void deleteRefreshToken(String token) {
        String redisKey = REFRESH_TOKEN_PREFIX + token;
        stringRedisTemplate.delete(redisKey);
    }
}