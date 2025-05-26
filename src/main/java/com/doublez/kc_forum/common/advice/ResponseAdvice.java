package com.doublez.kc_forum.common.advice;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.interceptor.ProactiveTokenRefreshInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Slf4j
@ControllerAdvice // 使用 @ControllerAdvice 或 @RestControllerAdvice 都可以
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 检查是否是 SpringDoc 相关请求 (保持不变)
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            String path = requestAttributes.getRequest().getRequestURI();
            // 稍微优化一下路径检查
            return !path.startsWith("/v3/api-docs") && !path.startsWith("/swagger-ui");
        }
        return true; // 让 beforeBodyWrite 处理所有情况
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        // 获取 HttpServletRequest
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        // 尝试获取新 Token
        Object newTokenAttr = servletRequest.getAttribute(ProactiveTokenRefreshInterceptor.NEW_ACCESS_TOKEN_ATTRIBUTE);

        String newAccessToken = (newTokenAttr instanceof String) ? (String) newTokenAttr : null;

        // 处理最终要返回的 Result 对象
        Result<?> finalResult; // 用来存储最终要返回的 Result 对象

        // 1. 如果 body 本身就是 Result 类型
        if (body instanceof Result<?>) {
            finalResult = (Result<?>) body;
        }
        // 2. 如果 body 是 null
        else if (body == null && !returnType.getParameterType().equals(void.class)) { // 避免包装 void 返回类型
            finalResult = Result.sucess(); 
        }
        // 3. 如果 body 是 String
        else if (body instanceof String) {
            // 特殊处理 String，需要序列化
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            // 先包装，再设置 Token
            Result<String> stringResult = Result.sucess((String) body);
            if (newAccessToken != null) {
                stringResult.setNewAccessToken(newAccessToken);
            }
            return objectMapper.writeValueAsString(stringResult); // 直接返回序列化后的 String
        }
        // 4. 其他所有情况，都需要包装
        else {
            finalResult = Result.sucess(body); // 假设默认包装为成功
        }

        //  将新 Token 设置到最终的 Result 对象中 (如果存在)
        if (newAccessToken != null && finalResult != null) {
            finalResult.setNewAccessToken(newAccessToken);
        }
        return finalResult; // 返回处理过的 Result 对象
    }
}

