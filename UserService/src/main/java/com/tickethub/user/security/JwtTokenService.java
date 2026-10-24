package com.tickethub.user.security;

import com.tickethub.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class JwtTokenService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        String roleName = user.getRole() != null ? user.getRole().name() : "CUSTOMER";
        JwtBuilder builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("preferred_username", user.getEmail())
                .claim("userId", user.getUserId())
                .claim("email", user.getEmail())
                .claim("roles", List.of("ROLE_" + roleName))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs));

        if (user.getPhone() != null) {
            builder.claim("phone", user.getPhone());
        }
        if (user.getFullName() != null) {
            builder.claim("fullName", user.getFullName());
        }
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
