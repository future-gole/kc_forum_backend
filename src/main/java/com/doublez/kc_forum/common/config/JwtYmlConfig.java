package com.doublez.kc_forum.common.config;

import com.doublez.kc_forum.common.utiles.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class JwtYmlConfig {

    public JwtYmlConfig(@Value("${jwt.secret}") String secret,
                          @Value("${jwt.access-token.expiration-ms}") Integer expirationMs) {
        // 通过构造函数注入并初始化 ，因为字段是final
        // 这种情况下，secret 和 expirationMs 字段可以是 final

        log.info("Initializing JwtUtil with configured properties via Constructor in JwtYmlConfig.");
        if (secret == null || secret.trim().isEmpty()) {
            log.error("JWT secret is not configured or is empty. Cannot initialize JwtUtil.");
            throw new IllegalStateException("JWT secret ('jwt.secret') is missing or empty in configuration.");
        }
        if (expirationMs == null) {
            log.error("JWT expiration is not configured. Cannot initialize JwtUtil.");
            throw new IllegalStateException("JWT expiration ('jwt.access-token.expiration-ms') is missing in configuration.");
        }
        JwtUtil.init(secret, expirationMs);
    }

}