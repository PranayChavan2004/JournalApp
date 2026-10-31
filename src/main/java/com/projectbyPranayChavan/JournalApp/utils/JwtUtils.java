package com.projectbyPranayChavan.JournalApp.utils;

import com.projectbyPranayChavan.JournalApp.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private final SecretKey key;
    private final long expirationMillis;
    private final String issuer;

    public JwtUtils(@Value("${jwt.secret:}") String secret,
                    @Value("${jwt.expiration-minutes:60}") long expirationMinutes,
                    @Value("${jwt.issuer:journal-app}") String issuer,
                    Environment environment) {

        byte[] keyBytes;
        if (secret == null || secret.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("JWT_SECRET must be set in production (at least 32 characters).");
            }
            log.warn("JWT_SECRET is not set: using a random key. Logins will be reset on every restart. "
                    + "Set JWT_SECRET to keep users signed in.");
            keyBytes = new byte[48];
            new SecureRandom().nextBytes(keyBytes);
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET is too short: use at least 32 characters (256 bits).");
            }
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMinutes * 60_000L;
        this.issuer = issuer;
    }

    public record ParsedToken(UUID userId, int version) {
    }

    /*
      The token identifies the user by their immutable id (not the username, which can change)
      and carries a version number so tokens can be revoked.
     */
    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("ver", user.getTokenVersion())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    public Optional<ParsedToken> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer version = claims.get("ver", Integer.class);
            return Optional.of(new ParsedToken(UUID.fromString(claims.getSubject()), version == null ? 0 : version));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }
}
