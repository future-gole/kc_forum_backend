package com.doublez.kc_forum.common.interceptor;

import com.doublez.kc_forum.common.utiles.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ProactiveTokenRefreshInterceptor implements HandlerInterceptor {

    // 注入主动刷新阈值配置
    @Value("${jwt.proactive-refresh.threshold-ms}")
    private long proactiveRefreshThresholdMs;

    // 定义存储原始 Claims 的请求属性名称
    public static final String CLAIMS_ATTRIBUTE = "Authorization";
    // 定义传递新 Access Token 的响应头名称
    public static final String NEW_ACCESS_TOKEN_ATTRIBUTE = "NewAccessToken";

    /**
     * 在 preHandle 中，如果 Token 验证通过 (未过期且有效)，
     * 将解析出的 Claims 存入 request attribute，供 postHandle 使用。
     * 注意：这个 preHandle 应该在原来的认证拦截器 之后 执行，
     *
     * 检查 Access Token 是否接近过期，如果是，生成新 Token 并放入响应头。
     *
     * @return true 继续执行，false 中断
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果是 OPTIONS 请求 (CORS 预检)，直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK); // 可以明确设置状态码为 200 OK
            return true;
        }

        // 检查是否配置了有效的刷新阈值
        if (proactiveRefreshThresholdMs <= 0) {
            return false; // 阈值无效或禁用，直接返回
        }

        String authorizationHeader = request.getHeader("Authorization");
        //获取token
        String token = authorizationHeader.substring(7);
        //获取Claims
        Claims claims = JwtUtil.parseToken(token);

        // 只有当 Claims 存在时才进行处理 (意味着 preHandle 验证通过且 Token 未过期)
        if (claims != null) {
            Date expiration = claims.getExpiration();
            if (expiration != null) {
                long now = System.currentTimeMillis();
                long remainingMillis = expiration.getTime() - now;

                // 判断剩余时间是否小于等于阈值，并且大于 0 (确保 Token 仍然有效)
                if (remainingMillis > 0 && remainingMillis <= proactiveRefreshThresholdMs) {
                    //取出原token中的信息
                    String email = String.valueOf(claims.get("email").toString());
                    try {
                        //放入载荷
                        Map<String,Object> accessTokenClaims  = new HashMap<>();

                        accessTokenClaims .put("email",email);
                        accessTokenClaims .put("Id", Long.valueOf(claims.get("Id").toString()));
                        //构建新的token
                        String newAccessToken = JwtUtil.genToken(accessTokenClaims);

                        //  修改点：将新 Token 存入 request attribute
                        request.setAttribute(NEW_ACCESS_TOKEN_ATTRIBUTE, newAccessToken);
                        log.info("Attribute '{}' 确认已设置到 request 对象", NEW_ACCESS_TOKEN_ATTRIBUTE); // 添加确认日志

                    } catch (Exception e) {
                        // 如果生成新 Token 失败，记录错误，但不影响原始请求的响应
                        log.error("为用户 '{}' 主动刷新 Access Token 时发生错误: {}", email, e.getMessage(), e);
                        // 这里不需要中断流程或返回错误给客户端，让后续的 401 -> 刷新机制处理即可
                    }
                }
            } else {
                log.warn("在 preHandle 中获取到的 Claims 没有过期时间，无法进行主动刷新判断。");
            }
        }
        return true; // 默认放行，让 postHandle 处理
    }
}