package com.doublez.kc_forum.common.config;

import com.doublez.kc_forum.common.interceptor.Interceptor;
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/user/register")
                .excludePathPatterns("/v3/api-docs")
                .excludePathPatterns("/user/verifyEmail")
                .excludePathPatterns("/user/sendVerificationCode")
                .excludePathPatterns("/swagger-ui/**");//记得需要排除静态文件，没有分离的情况下
    }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 添加 Swagger UI 资源映射
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springdoc-openapi-ui/")
                .resourceChain(false);
        registry.addResourceHandler("/articles/**")
                .addResourceLocations("file:" + "F:/upload/");
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