package com.doublez.kc_forum.common.config;

import com.doublez.kc_forum.common.interceptor.Interceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

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
    }
}