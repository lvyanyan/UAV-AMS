package com.uav.system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发/解析：claims 携带 sub/username/role/military/perms
 * perms 为该角色的权限码列表，网关与下游服务据此做方法级鉴权；权限变更后重新登录生效
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;
    private final long militaryExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.military-expiration-ms}") long militaryExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.militaryExpirationMs = militaryExpirationMs;
    }

    /** 签发携带权限码列表的 token */
    public String generateToken(Long userId, String username, String roleCode, boolean isMilitary,
                                List<String> perms) {
        long exp = isMilitary ? militaryExpirationMs : expirationMs;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", roleCode)
                .claim("military", isMilitary)
                .claim("perms", perms == null ? List.of() : perms)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + exp))
                .signWith(key)
                .compact();
    }

    /** 兼容旧签名（不带权限码） */
    public String generateToken(Long userId, String username, String roleCode, boolean isMilitary) {
        return generateToken(userId, username, roleCode, isMilitary, List.of());
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token) {
        try { parseToken(token); return true; }
        catch (JwtException e) { return false; }
    }

    @SuppressWarnings("unchecked")
    public List<String> getPerms(Claims claims) {
        Object raw = claims.get("perms");
        if (raw instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        }
        return List.of();
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getRoleCode(String token) {
        return parseToken(token).get("role", String.class);
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }
}
