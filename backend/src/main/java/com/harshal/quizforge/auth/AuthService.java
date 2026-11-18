package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.AuthConfig;
import com.harshal.quizforge.auth.AuthDtos.AuthResponse;
import com.harshal.quizforge.auth.AuthDtos.ChangePasswordRequest;
import com.harshal.quizforge.auth.AuthDtos.LoginRequest;
import com.harshal.quizforge.auth.AuthDtos.MeResponse;
import com.harshal.quizforge.auth.AuthDtos.RegisterRequest;
import com.harshal.quizforge.auth.RefreshToken.RevokeReason;
import com.harshal.quizforge.auth.RefreshTokenService.Rotation;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.ForbiddenException;
import com.harshal.quizforge.common.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuthService {

    static final String DEACTIVATED_MESSAGE = "This account is deactivated. Contact your administrator.";
    static final String SIGNUP_CLOSED_MESSAGE = "Sign-up is closed. Ask your administrator for an account.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptLimiter loginLimiter;
    private final Clock clock;
    private final boolean publicSignup;

    public AuthService(UserRepository users, PasswordEncoder encoder, AuthenticationManager authManager, JwtService jwt,
                       RefreshTokenService refreshTokens, LoginAttemptLimiter loginLimiter, Clock clock,
                       @Value("${app.auth.public-signup:false}") boolean publicSignup) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.loginLimiter = loginLimiter;
        this.clock = clock;
        this.publicSignup = publicSignup;
    }

    /** What the controller needs: the JSON body plus the raw refresh token for the cookie. */
    public record Session(AuthResponse response, String refreshToken) {}

    public AuthConfig config() {
        return new AuthConfig(publicSignup);
    }

    /** Self sign-up as a Learner. Off by default: accounts are created by an admin. */
    @Transactional
    public Session register(RegisterRequest req) {
        if (!publicSignup) {
            throw new ForbiddenException(SIGNUP_CLOSED_MESSAGE);
        }
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(AuthDtos.blankToNull(req.fullName()));
        user.setPasswordHash(encoder.encode(req.password()));
        user.setLastLoginAt(clock.instant());
        users.save(user);
        return sessionFor(user, refreshTokens.issue(user));
    }

    @Transactional
    public Session login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        loginLimiter.checkAllowed(email);
        try {
            // Throws BadCredentialsException (→ 401 via GlobalExceptionHandler) on wrong email/password.
            authManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        } catch (BadCredentialsException e) {
            loginLimiter.recordFailure(email);
            throw e;
        } catch (DisabledException e) {
            // Spring checks "disabled" BEFORE the password. Only say "deactivated" to someone who
            // knows the password; everyone else gets the usual message, so emails can't be probed.
            User user = users.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Bad credentials"));
            if (!encoder.matches(req.password(), user.getPasswordHash())) {
                loginLimiter.recordFailure(email);
                throw new BadCredentialsException("Bad credentials");
            }
            throw new ForbiddenException(DEACTIVATED_MESSAGE);
        }
        loginLimiter.recordSuccess(email);
        User user = users.findByEmail(email).orElseThrow();
        user.setLastLoginAt(clock.instant());
        return sessionFor(user, refreshTokens.issue(user));
    }

    /** Swaps a valid refresh token for a new access token + a new (rotated) refresh token. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Session refresh(String rawRefreshToken) {
        Rotation rotation = refreshTokens.rotate(rawRefreshToken);
        if (!rotation.user().isActive()) {
            // Belt and braces: deactivation already ends sessions, but never refresh a deactivated account.
            refreshTokens.revokeAll(rotation.user(), RevokeReason.DEACTIVATED);
            throw new InvalidRefreshTokenException("Account deactivated");
        }
        return sessionFor(rotation.user(), rotation.rawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    /**
     * The user picks a new password (also how a pending account becomes active). Every other
     * session is signed out; this browser gets a fresh one.
     */
    @Transactional
    public Session changePassword(String email, ChangePasswordRequest req) {
        User user = current(email);
        // 400, not 401: a 401 would make the frontend think the session expired.
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Your current password is incorrect");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new BadRequestException("Choose a password different from your current one");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        refreshTokens.revokeAll(user, RevokeReason.PASSWORD_CHANGED);
        return sessionFor(user, refreshTokens.issue(user));
    }

    @Transactional
    public MeResponse updateProfile(String email, String fullName) {
        User user = current(email);
        user.setFullName(AuthDtos.blankToNull(fullName));
        return me(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(String email) {
        return me(current(email));
    }

    private MeResponse me(User user) {
        return new MeResponse(user.getEmail(), user.getFullName(), user.getRole().name(),
                AuthDtos.permissionsOf(user), user.isMustChangePassword());
    }

    private User current(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Session sessionFor(User user, String refreshToken) {
        String role = user.getRole().name();
        AuthResponse body = new AuthResponse(
                jwt.generate(user.getEmail(), role), jwt.lifetime().toSeconds(),
                user.getEmail(), user.getFullName(), role, AuthDtos.permissionsOf(user), user.isMustChangePassword());
        return new Session(body, refreshToken);
    }
}
