package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.RefreshToken.RevokeReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    private RefreshTokenRepository repo;
    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new RefreshTokenService(repo, Clock.fixed(NOW, ZoneOffset.UTC), 7);
        user = new User();
        user.setId(1L);
        user.setEmail("me@test.com");
    }

    private RefreshToken stored(String raw, Instant expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setUser(user);
        t.setTokenHash(RefreshTokenService.hash(raw));
        t.setFamilyId(UUID.randomUUID());
        t.setCreatedAt(NOW.minus(Duration.ofDays(1)));
        t.setExpiresAt(expiresAt);
        return t;
    }

    @Test
    void issueStoresOnlyTheHashWithSevenDayExpiry() {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOneInSameFamily() {
        RefreshToken old = stored("old", NOW.plus(Duration.ofDays(3)));
        when(repo.findForUpdate(RefreshTokenService.hash("old"))).thenReturn(Optional.of(old));

        var rotation = service.rotate("old");

        assertThat(old.getRevokedAt()).isEqualTo(NOW);
        assertThat(old.getRevokeReason()).isEqualTo(RevokeReason.ROTATED);
        assertThat(rotation.rawToken()).isNotEqualTo("old");
        assertThat(rotation.user()).isSameAs(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(old.getFamilyId());
    }

    @Test
    void reusingRotatedTokenAfterGraceRevokesWholeFamily() {
        RefreshToken old = stored("old", NOW.plus(Duration.ofDays(3)));
        old.setRevokedAt(NOW.minus(Duration.ofMinutes(5)));
        old.setRevokeReason(RevokeReason.ROTATED);
        when(repo.findForUpdate(anyString())).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo).revokeFamily(old.getFamilyId(), NOW, RevokeReason.REUSE_DETECTED);
    }

    @Test
    void reuseWithinGraceWindowIsRejectedButNotTreatedAsTheft() {
        RefreshToken old = stored("old", NOW.plus(Duration.ofDays(3)));
        old.setRevokedAt(NOW.minus(Duration.ofSeconds(2)));   // another tab just rotated it
        old.setRevokeReason(RevokeReason.ROTATED);
        when(repo.findForUpdate(anyString())).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void loggedOutTokenIsRejectedWithoutTheftHandling() {
        RefreshToken old = stored("old", NOW.plus(Duration.ofDays(3)));
        old.setRevokedAt(NOW.minus(Duration.ofHours(1)));
        old.setRevokeReason(RevokeReason.LOGOUT);
        when(repo.findForUpdate(anyString())).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void expiredTokenIsRejected() {
        when(repo.findForUpdate(anyString())).thenReturn(Optional.of(stored("old", NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repo.findForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesFamily() {
        RefreshToken current = stored("cur", NOW.plus(Duration.ofDays(3)));
        when(repo.findForUpdate(RefreshTokenService.hash("cur"))).thenReturn(Optional.of(current));

        service.revoke("cur");

        verify(repo).revokeFamily(eq(current.getFamilyId()), eq(NOW), eq(RevokeReason.LOGOUT));
    }
}
