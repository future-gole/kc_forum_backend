package com.doublez.kc_forum.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisConfig {
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> likeScript() {
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/like.lua"));
        redisScript.setResultType(List.class);//lua脚本会返回list类型
        return redisScript;
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> unlikeScript() {
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/unlike.lua"));
        redisScript.setResultType(List.class);//lua脚本会返回list类型
        return redisScript;
    }
}
