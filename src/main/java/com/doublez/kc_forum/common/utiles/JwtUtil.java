package com.doublez.kc_forum.common.utiles;

import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.exception.BusinessException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class JwtUtil {

    // 静态字段，不再是 final，由外部初始化
    private static String mySecret;
    private static Integer tokenExpirationMs;

    private static Key key;

    public static final String USER_ID = "Id";
    public static final String EMAIL = "email";

    /**
     * 初始化 JwtUtil 的静态配置。
     * 此方法应在应用程序启动时由 Spring Bean 调用。
     *
     * @param secret         JWT 密钥 (Base64 编码)
     * @param expirationMs   Token 过期时间 (毫秒)
     */
    public static void init(String secret, Integer expirationMs) {
        Objects.requireNonNull(secret, "JWT secret 未设置.");
        Objects.requireNonNull(expirationMs, "JWT expiration 未设置.");

        if (secret.trim().isEmpty()) {
            log.error("JWT secret 为空. 初始化失败.");
            throw new IllegalArgumentException("JWT secret 不能为空.");
        }

        mySecret = secret;
        tokenExpirationMs = expirationMs;
        try {
            byte[] keyBytes = Decoders.BASE64.decode(mySecret);
            key = Keys.hmacShaKeyFor(keyBytes);
            log.info("JwtUtil 初始化成功. Expiration: {}ms", tokenExpirationMs);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode Base64 secret or create HMAC SHA key. Secret: [REDACTED]", e);
            // 抛出运行时异常，阻止应用在JWT配置错误时继续运行
            throw new IllegalStateException("Failed to initialize JwtUtil due to invalid secret key configuration.", e);
        }
    }

    /**
     * 生成token
     * @param map
     * @return
     */
    public static  String genToken(Map<String, Object> map) {
        return  Jwts.builder()
                .setClaims(map)
                .setIssuedAt(new Date())//设置开始时间
                .setExpiration(new Date(System.currentTimeMillis() + tokenExpirationMs))//设置过期时间
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

        JwtParser parser = Jwts.parserBuilder().setSigningKey(mySecret).build(); // 使用静态 signingKey
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
        if (claims != null && claims.containsKey(USER_ID)) {
            try {
                userId = Long.valueOf(claims.get(USER_ID).toString());
            } catch (NumberFormatException e) {
                // 处理 "id" 不是 Integer 类型的情况
                throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE,"用户id不为long类型");
            }
        }
        if (userId == null) {
            // 处理用户 ID 为空的情况
            throw new BusinessException(ResultCode.FAILED_CHECK_USERID);
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
        Object emailObject = claims.get(EMAIL);
        if (emailObject == null) {
            log.error("Token Claims 中缺少 'email' 字段. Claims: {}", claims);
            throw new BusinessException(ResultCode.FAILED_CHECK_USERID,"没有获取到用户邮箱");
        }

        try {
            // 尝试转换为 Long
            return String.valueOf(emailObject.toString());
        } catch (NumberFormatException e) {
            log.error("Token Claims 中的 'email' 字段格式非 Long: {}. Claims: {}", emailObject, claims);
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
    }

    private static Claims getClaims(HttpServletRequest request) throws ExpiredJwtException {
        final String authorizationHeader = request.getHeader("Authorization");
        final String bearerPrefix = "Bearer ";

        if (authorizationHeader == null || !authorizationHeader.startsWith(bearerPrefix)) {
            log.warn("请求头 Authorization 缺失或格式错误");
            // 返回更具体的错误码和消息
            throw new BusinessException(ResultCode.FAILED_UNAUTHORIZED);
        }

        final String token = authorizationHeader.substring(bearerPrefix.length());

        Claims claims;
        try {
            claims = JwtUtil.parseToken(token); // 调用可能抛出 ExpiredJwtException 的方法
            if (claims == null) {
                // parseToken 返回 null 表示非过期的其他解析错误
                log.warn("Token 无效 (非过期错误)");
                throw new BusinessException(ResultCode.FAILED_UNAUTHORIZED);
            }
        } catch (ExpiredJwtException e) {
            log.warn("尝试从请求获取用户ID时 Token 已过期");
            throw e; // 继续向上抛出过期异常
        }
        return claims;
    }
}