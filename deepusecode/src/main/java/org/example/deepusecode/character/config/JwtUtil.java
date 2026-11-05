package org.example.deepusecode.character.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    /** 明确禁止使用的占位密钥，防止把文档模板里的值带上线 */
    private static final java.util.Set<String> REJECTED_SECRETS = java.util.Set.of(
            "dev-local-only-secret-please-replace-in-production-2026",
            "change-me-at-least-32-characters-long"
    );

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret:}") String secret,
                   @Value("${jwt.expiration:86400000}") long expirationMs) {

        // 刻意不提供默认密钥：写死默认值等于把签发权交给所有人，
        // 任何人都能伪造任意 userId 的 token。缺失时直接启动失败。
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    缺少 JWT 密钥，应用无法安全启动。
                    请先设置环境变量 JWT_SECRET（至少 32 个字符）：
                      PowerShell : $env:JWT_SECRET="请换成你自己的随机字符串至少32位"
                      bash       : export JWT_SECRET="请换成你自己的随机字符串至少32位"
                    生成一个随机值：openssl rand -base64 48
                    """);
        }

        if (REJECTED_SECRETS.contains(secret.trim())) {
            throw new IllegalStateException(
                    "jwt.secret 仍是模板里的占位值，必须替换成真实随机密钥后再启动。"
                            + "生成方式：openssl rand -base64 48");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret 至少需要 32 字节（256 位），当前 " + keyBytes.length + " 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;

        // 只打印指纹，便于运维确认"线上用的到底是哪个密钥"，又不泄漏密钥本身
        log.info("JWT 密钥已加载，指纹 sha256:{}，长度 {} 字节，有效期 {} 小时",
                fingerprint(secret), keyBytes.length, expirationMs / 3_600_000);
    }

    private static String fingerprint(String secret) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String generateToken(Long userId, String account) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(account)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解析并校验签名与过期时间，失败返回 null（调用方按未登录处理）。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public Long extractUserId(Claims claims) {
        Object raw = claims.get("userId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
