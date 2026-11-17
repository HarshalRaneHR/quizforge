package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.AuthResponse;
import com.harshal.quizforge.auth.AuthDtos.LoginRequest;
import com.harshal.quizforge.auth.AuthDtos.RegisterRequest;
import com.harshal.quizforge.auth.AuthService.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return withRefreshCookie(HttpStatus.OK, authService.login(req));
    }

    /** Called by the frontend on page load and whenever the access token expires. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return sessionExpired();
        }
        try {
            return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
        } catch (InvalidRefreshTokenException e) {
            return sessionExpired();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, Session session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken(), refreshTokens.lifetime()).toString())
                .body(session.response());
    }

    private ResponseEntity<ProblemDetail> sessionExpired() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Session expired, please log in again"));
    }
}
