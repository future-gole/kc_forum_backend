package com.doublez.kc_forum.common.utiles;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
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

@Slf4j
public class JwtUtil {

    private static final String secret = "FzG6p48J80L6vFxLrQqy2JVN27NiYbgjtGuYCTpeX7w=";

    private static final Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));


    private static final Integer expiration = 3600000;

    /**
     * 生成token
     * @param map
     * @return
     */
    public static  String getToken(Map<String, Object> map) {
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
    public static Claims parseToken(String token) {
        //判断是不是为null
        if(!StringUtils.hasLength(token)) {
            return null;
        }
        //jwt解析器
        JwtParser bulid = Jwts.parserBuilder().setSigningKey(key).build();
        try {
            return bulid.parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            log.error("Token has expired. Token: {}, Exception: {}", token, e.getMessage(), e);
        } catch (SignatureException e) {
            log.error("Token signature is invalid. Token: {}, Exception: {}", token, e.getMessage(), e);
        } catch (MalformedJwtException e) {
            log.error("Token is malformed. Token: {}, Exception: {}", token, e.getMessage(), e);
        } catch (UnsupportedJwtException e) {
            log.error("Token is unsupported. Token: {}, Exception: {}", token, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.error("Token claims string is empty. Token: {}, Exception: {}", token, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Token parsing failed. Token: {}, Exception: {}", token, e.getMessage(), e);
        }
        return null;
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
        if (claims != null && claims.containsKey("id")) {
            try {
                userId = Long.valueOf(claims.get("id").toString());
            } catch (NumberFormatException e) {
                // 处理 "id" 不是 Integer 类型的情况
                throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
            }
        }
        if (userId == null) {
            // 处理用户 ID 为空的情况
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return userId;
    }
}
