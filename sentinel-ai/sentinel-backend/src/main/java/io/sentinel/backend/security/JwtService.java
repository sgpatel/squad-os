package io.sentinel.backend.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
@Service
public class JwtService {
    @Value("${sentinel.jwt.secret}") private String secret;
    @Value("${sentinel.jwt.expiry-ms:86400000}") private long expiryMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(UserEntity user) {
        return Jwts.builder()
            .subject(user.username)
            .claim("role", user.role.name())
            .claim("tenantId", user.tenantId)
            .claim("userId", user.id)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiryMs))
            .signWith(key())
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build()
            .parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try { parse(token); return true; }
        catch (Exception e) { return false; }
    }

    public String getUsername(String token) { return parse(token).getSubject(); }
    public String getRole(String token)     { return parse(token).get("role", String.class); }
    public String getTenantId(String token) { return parse(token).get("tenantId", String.class); }
}