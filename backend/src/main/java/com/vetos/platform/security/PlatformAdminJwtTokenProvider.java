package com.vetos.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JwtTokenProvider'in paralel esdegeri -- klinik personeli token'lariyla
 * AYNI imzalama sirrini kullanir ama yapisal olarak farkli bir claim
 * seti tasir (tenantId/branchIds/role YOK, "type":"platform-admin" VAR).
 * parse() bu claim'i dogrulayarak calinmis bir klinik token'inin platform
 * admin olarak cozumlenmesini yapisal olarak imkansiz kilar.
 */
@Component
public class PlatformAdminJwtTokenProvider {

    private static final String TOKEN_TYPE = "platform-admin";

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public PlatformAdminJwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-minutes:480}") long expirationMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(UUID platformAdminId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(platformAdminId.toString())
            .claim("email", email)
            .claim("type", TOKEN_TYPE)
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
            .signWith(signingKey)
            .compact();
    }

    public AuthenticatedPlatformAdmin parse(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        if (!TOKEN_TYPE.equals(claims.get("type", String.class))) {
            throw new JwtException("Token turu platform-admin degil");
        }

        return new AuthenticatedPlatformAdmin(
            UUID.fromString(claims.getSubject()),
            claims.get("email", String.class)
        );
    }
}
