//package com.tickethub.event.security;
//
//import io.jsonwebtoken.*;
//import io.jsonwebtoken.security.Keys;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.stereotype.Service;
//
//import javax.crypto.SecretKey;
//import java.nio.charset.StandardCharsets;
//import java.util.Date;
//
//@Service
//public class JwtService {
//
//    private final SecretKey key;
//
//    private final long expirationMs;
//
//    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-ms}") long expirationMs) {
//        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
//        this.expirationMs = expirationMs;
//    }
//
//    public String generateToken(UserDetails u) {
//        Date now = new Date();
//        return Jwts.builder().subject(u.getUsername()).issuedAt(now).expiration(new Date(now.getTime() + expirationMs)).claim("roles", u.getAuthorities().stream().map(a -> a.getAuthority()).toList()).signWith(key).compact();
//    }
//
//    public String extractUsername(String token) {
//        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
//    }
//
//    public boolean isValid(String token, UserDetails u) {
//        try {
//            return extractUsername(token).equals(u.getUsername()) && !Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getExpiration().before(new Date());
//        } catch (JwtException | IllegalArgumentException e) {
//            return false;
//        }
//    }
//
//    public long getExpirationMs() {
//        return expirationMs;
//    }
//}
