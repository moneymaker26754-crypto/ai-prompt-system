package com.jojo.prompt.common.utils;

import com.jojo.prompt.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    // 从配置文件读取密钥
    @Value("${jwt.secret:your-secret-key-must-be-at-least-32-characters-long-for-hs256-algorithm}")
    private String secretKey;

    // Token 有效期（7天）
    @Value("${jwt.expire-time:604800000}")
    private Long expireTime;

    /**
     * 生成 Token
     */
    public String createToken(Long userId, String username) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expireTime);

        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(userId.toString())  // 用户 ID
                .claim("username", username)  // 用户名
                .issuedAt(now)  // 签发时间
                .expiration(expireDate)  // 过期时间
                .signWith(key)  // 签名
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Token 解析失败: {}", e.getMessage());
            throw new BusinessException(401, "Token 无效或已过期");
        }
    }

    /**
     * 获取用户 ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 获取用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}