package com.vetos.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-minutes:480}") long expirationMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(UUID staffUserId, UUID tenantId, List<UUID> branchIds, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(staffUserId.toString())
            .claim("tenantId", tenantId.toString())
            .claim("branchIds", branchIds.stream().map(UUID::toString).collect(Collectors.toList()))
            .claim("role", role)
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
            .signWith(signingKey)
            .compact();
    }

    public AuthenticatedStaffUser parse(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        @SuppressWarnings("unchecked")
        List<String> branchIdStrings = claims.get("branchIds", List.class);

        return new AuthenticatedStaffUser(
            UUID.fromString(claims.getSubject()),
            UUID.fromString(claims.get("tenantId", String.class)),
            branchIdStrings.stream().map(UUID::fromString).toList(),
            claims.get("role", String.class)
        );
    }
}
