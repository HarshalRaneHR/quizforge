package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.AuthResponse;
import com.harshal.quizforge.auth.AuthDtos.ChangePasswordRequest;
import com.harshal.quizforge.auth.AuthDtos.MeResponse;
import com.harshal.quizforge.auth.AuthDtos.UpdateProfileRequest;
import com.harshal.quizforge.auth.AuthService.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's own account. The frontend calls /api/me on load to refresh role and permissions. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Account", description = "The signed-in user")
public class MeController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;

    @Operation(summary = "Current user's email, name, role and permissions",
            description = "mustChangePassword = true means every other endpoint answers 403 PASSWORD_CHANGE_REQUIRED until the password is changed.")
    @GetMapping("/api/me")
    public MeResponse me(@Parameter(hidden = true) Authentication auth) {
        return authService.me(auth.getName());
    }

    @Operation(summary = "Change your password",
            description = "Needs the current (or temporary) password. Signs out every other session and returns a fresh one for this browser.")
    @ApiResponse(responseCode = "200", description = "Password changed; new access token and refresh cookie")
    @ApiResponse(responseCode = "400", description = "Current password wrong, or new password invalid / unchanged")
    @PostMapping("/api/account/password")
    public ResponseEntity<AuthResponse> changePassword(@Parameter(hidden = true) Authentication auth,
                                                       @Valid @RequestBody ChangePasswordRequest req) {
        Session session = authService.changePassword(auth.getName(), req);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken(), refreshTokens.lifetime()).toString())
                .body(session.response());
    }

    @Operation(summary = "Update your name")
    @PutMapping("/api/account/profile")
    public MeResponse updateProfile(@Parameter(hidden = true) Authentication auth, @Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(auth.getName(), req.fullName());
    }
}
