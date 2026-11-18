package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.AuthResponse;
import com.harshal.quizforge.auth.AuthDtos.LoginRequest;
import com.harshal.quizforge.auth.AuthDtos.MeResponse;
import com.harshal.quizforge.auth.AuthDtos.RegisterRequest;
import com.harshal.quizforge.auth.RefreshTokenService.Rotation;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;

    /** What the controller needs: the JSON body plus the raw refresh token for the cookie. */
    public record Session(AuthResponse response, String refreshToken) {}

    @Transactional
    public Session register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        users.save(user);
        return sessionFor(user, refreshTokens.issue(user));
    }

    public Session login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        // Throws BadCredentialsException (→ 401 via GlobalExceptionHandler) on wrong email/password.
        authManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        User user = users.findByEmail(email).orElseThrow();
        return sessionFor(user, refreshTokens.issue(user));
    }

    /** Swaps a valid refresh token for a new access token + a new (rotated) refresh token. */
    public Session refresh(String rawRefreshToken) {
        Rotation rotation = refreshTokens.rotate(rawRefreshToken);
        return sessionFor(rotation.user(), rotation.rawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public MeResponse me(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
        return new MeResponse(user.getEmail(), user.getRole().name(), AuthDtos.permissionsOf(user));
    }

    private Session sessionFor(User user, String refreshToken) {
        String role = user.getRole().name();
        AuthResponse body = new AuthResponse(
                jwt.generate(user.getEmail(), role), jwt.lifetime().toSeconds(),
                user.getEmail(), role, AuthDtos.permissionsOf(user));
        return new Session(body, refreshToken);
    }
}
