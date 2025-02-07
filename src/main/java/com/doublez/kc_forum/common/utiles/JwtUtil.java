package com.doublez.kc_forum.common.utiles;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
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

    public static  String getToken(Map<String, Object> map) {
        return  Jwts.builder()
                .setClaims(map)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();

    }

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
}
