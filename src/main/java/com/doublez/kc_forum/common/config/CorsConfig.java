package com.doublez.kc_forum.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://119.91.218.96", // 你的服务器 IP
                        "http://localhost:5173"
                )// 保留本地开发环境的地址)
                .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                // 在这里暴露所有需要前端访问的响应头
                .exposedHeaders("Authorization")
                .maxAge(3600); // 预检请求缓存1小时
    }
}