package com.debateai.security;

import com.debateai.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT令牌服务
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiresSeconds;
    private final StringRedisTemplate redisTemplate;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expires-seconds}") long expiresSeconds,
            StringRedisTemplate redisTemplate
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiresSeconds = expiresSeconds;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成JWT令牌
     * @param user
     * @return 业务结果
     */
    public TokenResult generate(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiresSeconds);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new TokenResult(token, expiresSeconds);
    }

    /**
     * 解析JWT令牌
     * @param token
     * @return 业务结果
     */
    public AuthUser parse(String token) {
        return parseToken(token).authUser();
    }

    /**
     * 解析JWT令牌详细信息
     * @param token JWT令牌
     * @return 令牌解析结果
     */
    public TokenPayload parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        AuthUser authUser = new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class),
                claims.getId()
        );
        Instant issuedAt = claims.getIssuedAt() == null
                ? claims.getExpiration().toInstant().minusSeconds(expiresSeconds)
                : claims.getIssuedAt().toInstant();

        return new TokenPayload(authUser, issuedAt, claims.getExpiration().toInstant());
    }

    /**
     * 判断令牌是否需要滑动续期
     * @param tokenPayload 令牌解析结果
     * @return 是否需要续期
     */
    public boolean shouldRefresh(TokenPayload tokenPayload) {
        long totalSeconds = Duration.between(tokenPayload.issuedAt(), tokenPayload.expiresAt()).getSeconds();
        long remainingSeconds = Duration.between(Instant.now(), tokenPayload.expiresAt()).getSeconds();
        return totalSeconds > 0 && remainingSeconds > 0 && remainingSeconds * 10 < totalSeconds;
    }

    /**
     * 判断令牌是否已失效
     * @param jti
     * @return 业务结果
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(jti)));
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 加入令牌黑名单
     * @param jti
     */
    public void blacklist(String jti) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(blacklistKey(jti), "1", Duration.ofSeconds(expiresSeconds));
        } catch (Exception ignored) {
            // Redis is used only for logout token blacklist in the current MVP.
        }
    }

    private String blacklistKey(String jti) {
        return "jwt:blacklist:" + jti;
    }

    public record TokenResult(String token, long expiresIn) {
    }

    public record TokenPayload(AuthUser authUser, Instant issuedAt, Instant expiresAt) {
    }
}
