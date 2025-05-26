package com.doublez.kc_forum.common.interceptor;


import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.utiles.JwtUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;

@Component
@Slf4j
public class Interceptor implements HandlerInterceptor {

    private ObjectMapper objectMapper;

    public Interceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 使用AOP判断是否为非法登陆，其他地方就不用判断了
     * 前置处理，验证 Access Token
     * 关键在于区分 Token 过期和其他无效情况
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        log.info("认证头是：{}",request.getHeader("Authorization"));
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("认证失败: Authorization header 缺失或格式错误. URI: {}", request.getRequestURI());
            setUnauthorizedResponse(response, "请提供有效的认证令牌");
            return false;
        }
        String token = authorizationHeader.substring(7);

        try {
            Claims claims = JwtUtil.parseToken(token); // 尝试解析 Token

            if (claims != null) {
                // !! 关键改动：Token 有效，将 Claims 存入 request attribute !!
                // 使用 ProactiveTokenRefreshInterceptor 中定义的常量作为 Key
                request.setAttribute(ProactiveTokenRefreshInterceptor.CLAIMS_ATTRIBUTE, claims);
                log.debug("Token 验证通过. User ID: {}", claims.get("Id"));
                // 可选: 也可以单独存 UserID 等常用信息
                // request.setAttribute("userId", Long.valueOf(claims.get("Id").toString()));
                return true; // 放行请求
            } else {
                // parseToken 返回 null，表示 Token 无效（非过期原因）
                log.warn("认证失败: Token 无效 (非过期). URI: {}", request.getRequestURI());
                setUnauthorizedResponse(response, "令牌无效，请重新登录");
                return false;
            }
        } catch (ExpiredJwtException e) {
            // Token 过期，按原逻辑处理 (返回 401，让客户端触发 Refresh Token 流程)
            log.warn("认证失败: Access Token 已过期. URI: {}", request.getRequestURI());
            setUnauthorizedResponse(response, "访问令牌已过期");
            return false;
        } catch (Exception e) {
            log.error("认证失败: Token 解析时发生未知错误. URI: {}", request.getRequestURI(), e);
            setUnauthorizedResponse(response, "认证处理异常");
            return false;
        }
    }
    // 设置 401 Unauthorized 响应
    private void setUnauthorizedResponse(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<Object> errorResult =  new Result<>(ResultCode.FAILED_FORBIDDEN.getCode(), message);
        String json = objectMapper.writeValueAsString(errorResult);
        response.getWriter().write(json);
    }
}
