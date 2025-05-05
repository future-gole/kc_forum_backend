package com.doublez.kc_forum.common.config;

import com.doublez.kc_forum.common.interceptor.Interceptor;
import com.doublez.kc_forum.common.interceptor.ProactiveTokenRefreshInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

//    // 文件上传路径配置
//    private final String uploadDirectory = System.getProperty("user.dir") + "/storage/";

    @Value("${upload.avatar-base-path}")
    private String avatarBasePath;

    @Autowired
    private Interceptor interceptor;

    @Autowired
    private ProactiveTokenRefreshInterceptor proactiveRefreshInterceptor; // 注入主动刷新拦截器


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
//                .addPathPatterns("/**") // 拦截所有路径
                // --- 排除不需要认证的路径 ---
                .excludePathPatterns(
                        "/api/token/refresh",
                        "/user/login",                  // 登录接口
                        "/user/register",               // 注册接口 (如果公开)
                        "/api/token/refresh",           // !! Token 刷新接口 !!
                        "/email/**",                    // 邮件相关 (如果公开)
                        "/user/sendVerificationCode",   // 发送验证码 (如果公开)
                        "/user/verifyEmail",            // 验证邮箱 (如果公开)
                        // --- Swagger/OpenAPI ---
                        "/v3/api-docs/**",              // OpenAPI v3 JSON/YAML 定义
                        "/swagger-ui/**",               // Swagger UI 页面及资源
                        // --- 静态资源 ---
                        "/avatars/**",                  // 头像资源
                        "/articles/**",                 // 文章资源 (如果公开)
                        // --- 其他 ---
                        "/error"                        // Spring Boot 默认错误处理页
                        // ... 其他需要排除的公共路径
                )
                .order(1); // 设置拦截器执行顺序

        // 2. 注册主动刷新拦截器 (后执行)
        registry.addInterceptor(proactiveRefreshInterceptor)
                .addPathPatterns("/**") // 应用到所有路径 (因为它依赖 preHandle 设置的 attribute)
                .excludePathPatterns( // 排除公共路径 (同上)
                        "/api/token/refresh",
                        "/user/login",
                        "/user/register",
                        "/user/login",                  // 登录接口
                        "/user/register",               // 注册接口 (如果公开)
                        "/api/token/refresh",           // !! Token 刷新接口 !!
                        "/email/**",                    // 邮件相关 (如果公开)
                        "/user/sendVerificationCode",   // 发送验证码 (如果公开)
                        "/user/verifyEmail",            // 验证邮箱 (如果公开)
                        // --- Swagger/OpenAPI ---
                        "/v3/api-docs/**",              // OpenAPI v3 JSON/YAML 定义
                        "/swagger-ui/**",               // Swagger UI 页面及资源
                        // --- 静态资源 ---
                        "/avatars/**",                  // 头像资源
                        "/articles/**",                 // 文章资源 (如果公开)
                        // --- 其他 ---
                        "/error"                        // Spring Boot 默认错误处理页
                )
                .order(2); // !! 设置执行顺序为 2 (在认证之后) !!
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 添加 Swagger UI 资源映射
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springdoc-openapi-ui/")
                .resourceChain(false);
        registry.addResourceHandler("/articles/**")
                .addResourceLocations("file:" + "/var/");
        // 添加 avatars 资源映射
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + avatarBasePath + "/");
    }

    /**
     * 配置文件上传解析器
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }


}