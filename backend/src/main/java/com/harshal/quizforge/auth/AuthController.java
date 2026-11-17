package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.AuthResponse;
import com.harshal.quizforge.auth.AuthDtos.LoginRequest;
import com.harshal.quizforge.auth.AuthDtos.RegisterRequest;
import com.harshal.quizforge.auth.AuthService.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Auth", description = "Register, log in, refresh and end sessions")
@SecurityRequirements   // public endpoints: no bearer token needed
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;

    @Operation(summary = "Create an account (role LEARNER)",
            description = "Returns an access token and sets the HttpOnly refresh-token cookie.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(req));
    }

    @Operation(summary = "Log in",
            description = "Returns an access token (paste it into Authorize) and sets the refresh-token cookie.")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "401", description = "Invalid email or password", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return withRefreshCookie(HttpStatus.OK, authService.login(req));
    }

    /** Called by the frontend on page load and whenever the access token expires. */
    @Operation(summary = "Get a new access token using the refresh-token cookie",
            description = "Rotates the refresh token. Reusing an already-rotated token revokes the whole session.")
    @ApiResponse(responseCode = "200", description = "New access token; cookie rotated",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing, expired or revoked refresh token", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Parameter(hidden = true)
                                     @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return sessionExpired();
        }
        try {
            return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
        } catch (InvalidRefreshTokenException e) {
            return sessionExpired();
        }
    }

    @Operation(summary = "Log out", description = "Revokes the session on the server and clears the cookie.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Parameter(hidden = true)
                                       @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
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
