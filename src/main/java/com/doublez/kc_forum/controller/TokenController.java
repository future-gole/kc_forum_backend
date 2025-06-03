package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.service.impl.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/token") // 刷新接口的路径
public class TokenController {

    @Autowired
    private RefreshTokenService refreshTokenService;
    @Value("${jwt.refresh-token.expiration-ms}")
    private  long newRefreshTokenExpirationMillis;

    // 假设你有一个 UserService 来根据用户名获取用户ID
    // @Autowired
    // private UserService userService;

    /**
     * 刷新 Access Token 的接口
     */
    @PostMapping("/refresh")
// !! 不再需要 @RequestBody, 注入 HttpServletResponse, 使用 @CookieValue 读取 Cookie !!
    public Result<?> refreshToken(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenFromCookie, // 读取名为 "refreshToken" 的 Cookie
            HttpServletResponse response, HttpServletRequest request) {

        if (refreshTokenFromCookie == null || refreshTokenFromCookie.isEmpty()) {
            log.warn("刷新请求中缺少 'refreshToken' Cookie");
            throw new BusinessException(ResultCode.FAIL_REFRESH_TOKEN);
        }

        // 1. 验证从 Cookie 获取的 Refresh Token 是否在 Redis 中有效
        return refreshTokenService.validateRefreshToken(refreshTokenFromCookie) // validateRefreshToken 内部查询 Redis
                .map(userCookie -> { // 如果有效，返回关联的用户名
                    // --- Refresh Token 有效 ---
                    log.info("Refresh Token 有效，用户: {}", userCookie);

                    // --- 执行 Refresh Token Rotation ---
                    // a. (可选但推荐) 从 Redis 删除旧的 Refresh Token
                    refreshTokenService.deleteRefreshToken(refreshTokenFromCookie);

                    // b. 生成新的 Refresh Token (随机字符串)
                    String newRefreshTokenString = UUID.randomUUID().toString();

                    // c. 存储新的 Refresh Token 到 Redis
                    refreshTokenService.createRefreshToken(newRefreshTokenString);
                    // !! 替换为你的 Redis 存储逻辑 !!

                    // d. 生成新的 Access Token (JWT)
                    Long userId = JwtUtil.getUserId(request);
                    String userEmail = JwtUtil.getUserEmail(request);
                    Map<String, Object> newAccessTokenClaims = new HashMap<>();
                    newAccessTokenClaims.put("Id", userId);
                    newAccessTokenClaims.put("email", userEmail);
                    String newAccessToken = JwtUtil.genToken(newAccessTokenClaims);

                    // e. !! 设置新的 Refresh Token 到 HttpOnly Cookie !!
                    Cookie newRefreshTokenCookie = new Cookie("refreshToken", newRefreshTokenString);
//                    newRefreshTokenCookie.setHttpOnly(true);
//                    newRefreshTokenCookie.setSecure(true); // 生产环境 true
//                    newRefreshTokenCookie.setPath("/api/token"); // 与登录时设置的路径一致
                    newRefreshTokenCookie.setMaxAge((int) (newRefreshTokenExpirationMillis / 1000));
                    // ... 其他 Cookie 属性 (Domain, SameSite) ...
                    response.addCookie(newRefreshTokenCookie);

                    // f. 返回新的 Access Token 到响应体
                    log.info("成功刷新 Token，用户: {}", userId);
                    return new Result<>(newAccessToken); // 响应体只有 Access Token
                })
                // 2. 如果 validateRefreshToken 返回空 Optional，说明 Refresh Token 无效或已过期
                .orElseGet(() -> {
                    log.warn("无效或已过期的 Refresh Token (来自 Cookie): {}", refreshTokenFromCookie);
                    // 清除可能残留的无效 Cookie (可选但推荐)
                    Cookie expiredCookie = new Cookie("refreshToken", null);
                    expiredCookie.setHttpOnly(true);
                    expiredCookie.setSecure(true);
                    expiredCookie.setPath("/api/token");
                    expiredCookie.setMaxAge(0); // 设置 MaxAge=0 使 Cookie 立即过期
                    response.addCookie(expiredCookie);
                    throw new BusinessException(ResultCode.FAILED_UNAUTHORIZED);
                });
    }
}