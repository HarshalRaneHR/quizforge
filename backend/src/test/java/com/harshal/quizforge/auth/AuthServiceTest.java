package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.ChangePasswordRequest;
import com.harshal.quizforge.auth.AuthDtos.LoginRequest;
import com.harshal.quizforge.auth.AuthDtos.RegisterRequest;
import com.harshal.quizforge.auth.RefreshToken.RevokeReason;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ForbiddenException;
import com.harshal.quizforge.common.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock AuthenticationManager authManager;
    @Mock JwtService jwt;
    @Mock RefreshTokenService refreshTokens;

    LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, 15);
    User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail("sam@test.com");
        user.setPasswordHash("HASH");
        when(users.findByEmail("sam@test.com")).thenReturn(Optional.of(user));
        when(encoder.matches("right-password", "HASH")).thenReturn(true);
        when(jwt.generate(any(), any())).thenReturn("ACCESS");
        when(jwt.lifetime()).thenReturn(Duration.ofMinutes(15));
        when(refreshTokens.issue(any())).thenReturn("REFRESH");
    }

    private AuthService service(boolean publicSignup) {
        return new AuthService(users, encoder, authManager, jwt, refreshTokens, limiter,
                Clock.fixed(NOW, ZoneOffset.UTC), publicSignup);
    }

    @Test
    void signUpIsClosedByDefault() {
        assertThat(service(false).config().publicSignup()).isFalse();
        assertThatThrownBy(() -> service(false).register(new RegisterRequest("new@test.com", "password123", "New")))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("Ask your administrator");
        verify(users, never()).save(any());
    }

    @Test
    void signUpWorksWhenEnabled() {
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var session = service(true).register(new RegisterRequest("new@test.com", "password123", " New Person "));
        assertThat(session.response().role()).isEqualTo("LEARNER");
        assertThat(session.response().fullName()).isEqualTo("New Person");
        assertThat(session.response().mustChangePassword()).isFalse();
    }

    @Test
    void loginRecordsLastLoginAndReportsPendingPasswordChange() {
        user.setMustChangePassword(true);
        var session = service(false).login(new LoginRequest("Sam@Test.com", "right-password"));
        assertThat(session.response().mustChangePassword()).isTrue();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void deactivatedIsOnlyRevealedToSomeoneWithTheRightPassword() {
        user.setStatus(AccountStatus.DEACTIVATED);
        when(authManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> service(false).login(new LoginRequest("sam@test.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service(false).login(new LoginRequest("sam@test.com", "right-password")))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("deactivated");
        verify(refreshTokens, never()).issue(any());
    }

    @Test
    void tooManyFailedLoginsAreRefusedEvenWithTheRightPassword() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        AuthService service = service(false);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("sam@test.com", "guess"))).isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> service.login(new LoginRequest("sam@test.com", "right-password")))
                .isInstanceOf(TooManyRequestsException.class).hasMessageContaining("Too many failed login attempts");
        // Other emails are unaffected
        limiter.checkAllowed("someone-else@test.com");
    }

    @Test
    void successfulLoginClearsFailedAttempts() {
        limiter.recordFailure("sam@test.com");
        limiter.recordFailure("sam@test.com");
        service(false).login(new LoginRequest("sam@test.com", "right-password"));
        limiter.recordFailure("sam@test.com");
        limiter.recordFailure("sam@test.com");
        limiter.checkAllowed("sam@test.com");   // 2 of 3 used: still allowed
    }

    @Test
    void changePasswordClearsPendingFlagAndSignsOutOtherSessions() {
        user.setMustChangePassword(true);
        when(encoder.encode("brand-new-pass")).thenReturn("NEWHASH");

        var session = service(false).changePassword("sam@test.com", new ChangePasswordRequest("right-password", "brand-new-pass"));

        assertThat(user.getPasswordHash()).isEqualTo("NEWHASH");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(session.response().mustChangePassword()).isFalse();
        assertThat(session.refreshToken()).isEqualTo("REFRESH");
        verify(refreshTokens).revokeAll(user, RevokeReason.PASSWORD_CHANGED);
    }

    @Test
    void changePasswordNeedsTheCurrentOneAndANewOne() {
        assertThatThrownBy(() -> service(false).changePassword("sam@test.com", new ChangePasswordRequest("nope", "brand-new-pass")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("current password");
        assertThatThrownBy(() -> service(false).changePassword("sam@test.com", new ChangePasswordRequest("right-password", "right-password")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("different");
    }

    @Test
    void refreshIsRefusedForADeactivatedAccount() {
        user.setStatus(AccountStatus.DEACTIVATED);
        when(refreshTokens.rotate("OLD")).thenReturn(new RefreshTokenService.Rotation(user, "NEW"));

        assertThatThrownBy(() -> service(false).refresh("OLD")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens).revokeAll(user, RevokeReason.DEACTIVATED);
    }
}
