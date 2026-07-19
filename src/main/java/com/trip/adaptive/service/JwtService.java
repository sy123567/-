package com.trip.adaptive.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
  private final SecretKey signingKey;
  private final long expiration;

  public JwtService(
      @Value("${app.jwt.secret:dev-only-change-this-jwt-secret-to-a-long-random-value}")
          String secret,
      @Value("${app.jwt.expiration:86400000}") long expiration) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("JWT secret must be at least 32 UTF-8 bytes");
    }
    signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expiration = expiration;
  }

  public String issue(User user) {
    Date now = new Date();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expiration))
        .signWith(signingKey)
        .compact();
  }

  public Long userId(String token) {
    Claims claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    return Long.valueOf(claims.getSubject());
  }
}
