package com.tickethub.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Service
@Slf4j
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final String keycloakPublicKeyBase64;
    private final String keycloakIssuerUri;
    private PublicKey rsaPublicKey;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.keycloak.public-key:}") String keycloakPublicKey,
            @Value("${app.keycloak.issuer-uri:}") String keycloakIssuerUri) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.keycloakPublicKeyBase64 = keycloakPublicKey;
        this.keycloakIssuerUri = keycloakIssuerUri;
        if (keycloakPublicKey != null && !keycloakPublicKey.isBlank()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(keycloakPublicKey.replaceAll("\\s+", ""));
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                this.rsaPublicKey = keyFactory.generatePublic(spec);
                log.info("Initialized RSA Public Key for Keycloak OIDC verification");
            } catch (Exception e) {
                log.warn("Could not parse Keycloak RSA public key: {}", e.getMessage());
            }
        }
    }

    public String generateToken(String username, String role) {
        return generateToken(null, username, null, role);
    }

    public String generateToken(Long userId, String username, String email, String role) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(username)
                .claim("roles", List.of("ROLE_" + role))
                .claim("preferred_username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs));
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (email != null && !email.isBlank()) {
            builder.claim("email", email);
        }
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        // 1. Try HMAC secret verification first (for native gateway & user-service tokens)
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception ex) {
            // 2. Try Keycloak RSA public key verification if available
            if (rsaPublicKey != null) {
                try {
                    return Jwts.parser().verifyWith(rsaPublicKey).build().parseSignedClaims(token).getPayload();
                } catch (Exception rsaEx) {
                    log.debug("RSA token validation failed: {}", rsaEx.getMessage());
                }
            }

            throw new IllegalArgumentException("Invalid JWT token", ex);
        }
    }

    public String extractUser(Claims claims) {
        Object pref = claims.get("preferred_username");
        if (pref != null && !pref.toString().isBlank()) {
            return pref.toString();
        }
        return claims.getSubject() != null ? claims.getSubject() : "anonymous";
    }

    public String extractEmail(Claims claims) {
        Object email = claims.get("email");
        return email != null ? email.toString() : "";
    }

    public String extractUserId(Claims claims) {
        Object uid = claims.get("userId");
        if (uid == null) uid = claims.get("user_id");
        if (uid != null) return uid.toString();
        String sub = claims.getSubject();
        if (sub != null && sub.matches("\\d+")) return sub;
        return null;
    }

    public String extractRole(Claims claims) {
        // 1. Keycloak realm_access.roles
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object rolesObj = map.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                for (Object r : roles) {
                    String roleStr = String.valueOf(r).toUpperCase();
                    if (roleStr.contains("ADMIN")) return "ADMIN";
                    if (roleStr.contains("EVENT_MANAGER") || roleStr.contains("MANAGER")) return "EVENT_MANAGER";
                }
                for (Object r : roles) {
                    String roleStr = String.valueOf(r).toUpperCase();
                    if (roleStr.contains("CUSTOMER") || roleStr.contains("USER")) return "CUSTOMER";
                }
            }
        }

        // 2. Native 'roles' collection
        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> collection && !collection.isEmpty()) {
            String primary = String.valueOf(collection.iterator().next()).replace("ROLE_", "");
            return primary;
        }

        return "CUSTOMER";
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}

