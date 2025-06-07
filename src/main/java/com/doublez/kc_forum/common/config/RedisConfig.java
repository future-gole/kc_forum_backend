package com.doublez.kc_forum.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 1. 先创建和配置 ObjectMapper
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // JdkSubTypeValidator.instance 替代 LaissezFaireSubTypeValidator.instance (如果 spring-boot 版本 >= 2.5.x 且 jackson 版本支持)
        // 或者使用其他的 SubTypeValidator 实现，LaissezFaireSubTypeValidator 在较新版本中可能存在安全风险提示
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        om.registerModule(new JavaTimeModule()); // 支持 Java 8 日期时间
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 日期不序列化为时间戳

        // 2. 在构造 Jackson2JsonRedisSerializer 时传入 ObjectMapper
        Jackson2JsonRedisSerializer<Object> jacksonSeial = new Jackson2JsonRedisSerializer<>(om, Object.class);
        // jacksonSeial.setObjectMapper(om); // 这行代码需要被移除

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Key 采用 String 的序列化方式
        template.setKeySerializer(stringRedisSerializer);
        // Hash 的 Key 也采用 String 的序列化方式
        template.setHashKeySerializer(stringRedisSerializer);
        // Value 序列化方式采用 Jackson
        template.setValueSerializer(jacksonSeial);
        // Hash 的 Value 序列化方式采用 Jackson
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }
}
