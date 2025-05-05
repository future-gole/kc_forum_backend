package com.doublez.kc_forum.common.utiles;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

@Slf4j
//@Component // !! 确保此类被 Spring 扫描并作为 Bean 管理 !!
public class JwtUtil {

//    // --- 用于接收注入值的实例字段 ---
//    @Value("${jwt.secret}")
//    private static String injectedSecret;
//
//    @Value("${jwt.access-token.expiration-ms}")
//    private long injectedAccessTokenExpirationMs;
//
//    @Value("${jwt.refresh-token.expiration-ms}")
//    private long injectedRefreshTokenExpirationMs;

    private static final String secret = "FzG6p48J80L6vFxLrQqy2JVN27NiYbgjtGuYCTpeX7w=";

    private static final Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));


    private static final Integer expiration = 10000;



    /**
     * 生成token
     * @param map
     * @return
     */
    public static  String genToken(Map<String, Object> map) {
        return  Jwts.builder()
                .setClaims(map)
                .setIssuedAt(new Date())//设置开始时间
                .setExpiration(new Date(System.currentTimeMillis() + expiration))//设置过期时间
                .signWith(key)
                .compact();

    }

    /**
     * 校验token
     * @param token
     * @return
     */
    /**
     * 解析和校验 Token。
     * 如果 Token 有效，返回 Claims。
     * 如果 Token 过期，抛出 ExpiredJwtException。
     * 如果 Token 无效（签名错误、格式错误等），返回 null。
     *
     * @param token 要解析的 JWT 字符串
     * @return 解析出的 Claims 对象，如果无效则返回 null
     * @throws ExpiredJwtException 如果令牌已过期
     */
    public static Claims parseToken(String token) throws ExpiredJwtException {
        // 确保初始化已完成
        if (!StringUtils.hasLength(token)) {
            log.warn("尝试解析的 Token 为空或 null");
            return null;
        }

        JwtParser parser = Jwts.parserBuilder().setSigningKey(secret).build(); // 使用静态 signingKey
        try {
            return parser.parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期. Token: {}", token);
            throw e; // 将过期异常抛出
        } catch (UnsupportedJwtException e) {
            log.error("不支持的 Token 格式. Token: {}", token, e);
        } catch (MalformedJwtException e) {
            log.error("Token 格式错误. Token: {}", token, e);
        } catch (SignatureException e) {
            log.error("Token 签名无效. Token: {}", token, e);
        } catch (IllegalArgumentException e) {
            log.error("Token 参数错误. Token: {}", token, e);
        } catch (Exception e) {
            log.error("Token 解析失败. Token: {}", token, e);
        }
        return null; // 其他错误返回 null
    }

    /**
     * 从请求头中获取当前用户id
     * @param request
     * @return
     */
    public static Long getUserId(HttpServletRequest request) {
        // 1. 从请求头中获取 Authorization Header
        String authorizationHeader = request.getHeader("Authorization");
        String token = authorizationHeader.substring(7);

        // 2. 验证 JWT 并解析 Token (Interceptor 已经验证过 Token，这里只需要解析)
        Claims claims = JwtUtil.parseToken(token);

        // 3. 从 claims 中获取用户 ID
        Long userId = null;
        if (claims != null && claims.containsKey("Id")) {
            try {
                userId = Long.valueOf(claims.get("Id").toString());
            } catch (NumberFormatException e) {
                // 处理 "id" 不是 Integer 类型的情况
                throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
            }
        }
        if (userId == null) {
            // 处理用户 ID 为空的情况
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CHECK_USERID));
        }
        return userId;
    }

    /**
     * 从 HttpServletRequest 中获取用户 邮箱。
     *
     * @param request HttpServletRequest 对象
     * @return 用户 ID (Long 类型)
     * @throws ApplicationException 如果 Header 缺失、Token 解析失败(非过期)、ID 缺失或格式错误
     * @throws ExpiredJwtException 如果 Token 已过期 (由 parseToken 抛出)
     */
    public static String getUserEmail(HttpServletRequest request) throws ExpiredJwtException {
        Claims claims = getClaims(request);
        // 不需要捕捉 Exception，因为 parseToken 内部已处理

        // 从 Claims 中获取用户 ID
        Object emailObject = claims.get("email");
        if (emailObject == null) {
            log.error("Token Claims 中缺少 'email' 字段. Claims: {}", claims);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CHECK_USERID)); // 使用你定义的枚举
        }

        try {
            // 尝试转换为 Long
            return String.valueOf(emailObject.toString());
        } catch (NumberFormatException e) {
            log.error("Token Claims 中的 'email' 字段格式非 Long: {}. Claims: {}", emailObject, claims);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private static Claims getClaims(HttpServletRequest request) throws ExpiredJwtException {
        final String authorizationHeader = request.getHeader("Authorization");
        final String bearerPrefix = "Bearer ";

        if (authorizationHeader == null || !authorizationHeader.startsWith(bearerPrefix)) {
            log.warn("请求头 Authorization 缺失或格式错误");
            // 返回更具体的错误码和消息
            throw new ApplicationException(Result.failed(ResultCode.FAILED_UNAUTHORIZED));
        }

        final String token = authorizationHeader.substring(bearerPrefix.length());

        Claims claims;
        try {
            claims = JwtUtil.parseToken(token); // 调用可能抛出 ExpiredJwtException 的方法
            if (claims == null) {
                // parseToken 返回 null 表示非过期的其他解析错误
                log.warn("Token 无效 (非过期错误)");
                throw new ApplicationException(Result.failed(ResultCode.FAILED_UNAUTHORIZED));
            }
        } catch (ExpiredJwtException e) {
            log.warn("尝试从请求获取用户ID时 Token 已过期");
            throw e; // 继续向上抛出过期异常
        }
        return claims;
    }
}