package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.RefreshToken.RevokeReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 * <ul>
 *   <li>Each refresh revokes the presented token and issues a new one in the same family.</li>
 *   <li>If an already-rotated token is presented again (outside a short grace window for
 *       racing browser tabs), it was probably stolen: the whole family is revoked.</li>
 * </ul>
 */
@Slf4j
@Service
public class RefreshTokenService {

    /** Two tabs refreshing at once is normal; only treat reuse as theft after this window. */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final Clock clock;
    private final Duration lifetime;

    public RefreshTokenService(RefreshTokenRepository tokens, Clock clock,
                               @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        this.tokens = tokens;
        this.clock = clock;
        this.lifetime = Duration.ofDays(refreshTokenDays);
    }

    public record Rotation(User user, String rawToken) {}

    public Duration lifetime() {
        return lifetime;
    }

    /** Starts a new session (new family) and returns the raw token for the cookie. */
    @Transactional
    public String issue(User user) {
        return create(user, UUID.randomUUID());
    }

    // noRollbackFor: when reuse is detected, revoking the family must be committed even though we throw.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = tokens.findForUpdate(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        if (current.getRevokedAt() != null) {
            // Only a token that was already rotated can signal theft (someone else holds a copy).
            // Logged-out or already-revoked families are simply rejected.
            boolean rotated = current.getRevokeReason() == RevokeReason.ROTATED;
            boolean racingTab = current.getRevokedAt().plus(REUSE_GRACE).isAfter(now);
            if (rotated && !racingTab) {
                int revoked = tokens.revokeFamily(current.getFamilyId(), now, RevokeReason.REUSE_DETECTED);
                log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}",
                        current.getUser().getId(), revoked, current.getFamilyId());
            }
            throw new InvalidRefreshTokenException("Refresh token was already used");
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        current.setRevokedAt(now);
        current.setRevokeReason(RevokeReason.ROTATED);
        String next = create(current.getUser(), current.getFamilyId());
        return new Rotation(current.getUser(), next);
    }

    /** Logout: ends this session on every tab/device that shares it. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findForUpdate(hash(rawToken))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), clock.instant(), RevokeReason.LOGOUT));
    }

    /** Signs the user out everywhere (password change, admin reset, deactivation). */
    @Transactional
    public void revokeAll(User user, RevokeReason reason) {
        int revoked = tokens.revokeAllForUser(user.getId(), clock.instant(), reason);
        if (revoked > 0) log.info("Ended {} session token(s) for user {} ({})", revoked, user.getId(), reason);
    }

    /** Housekeeping: expired tokens are useless, delete them nightly. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = tokens.deleteExpired(clock.instant());
        if (deleted > 0) log.info("Purged {} expired refresh tokens", deleted);
    }

    private String create(User user, UUID familyId) {
        byte[] bytes = new byte[32];   // 256 bits of randomness
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setFamilyId(familyId);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(lifetime));
        tokens.save(token);
        return raw;
    }

    // A fast hash is fine here (unlike passwords): the token is 256 random bits, so it can't be brute-forced.
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
