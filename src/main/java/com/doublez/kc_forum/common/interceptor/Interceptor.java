package com.doublez.kc_forum.common.interceptor;


import com.doublez.kc_forum.common.utiles.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class Interceptor implements HandlerInterceptor {

    /**
     * 使用AOP判断是否为非法登陆，其他地方就不用判断了
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取 Authorization Header
        String authorizationHeader = request.getHeader("Authorization");

        // 检查 Authorization Header 是否存在 并且 校验 Token 是否有效
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")
                || JwtUtil.parseToken(authorizationHeader.substring(7)) == null) {
            log.warn("非法登录");
            setUnauthorizedResponse(response, "非法登录");
            return false;
        }

        return true;
    }

    // 设置 401 Unauthorized 响应
    private void setUnauthorizedResponse(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"code\": %d, \"message\": \"%s\"}", HttpServletResponse.SC_UNAUTHORIZED, message));
    }
}
