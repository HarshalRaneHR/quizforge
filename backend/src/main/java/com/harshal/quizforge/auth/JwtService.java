package com.harshal.quizforge.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Short-lived access tokens. Long sessions come from refresh tokens (RefreshTokenService). */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration lifetime;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
                      Clock clock) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.lifetime = Duration.ofMinutes(accessTokenMinutes);
        this.clock = clock;
    }

    public Duration lifetime() {
        return lifetime;
    }

    public String generate(String email, String role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)   // informational only; authorities are loaded from the DB per request
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /** Throws io.jsonwebtoken.JwtException if the token is invalid or expired. */
    public String extractEmail(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
