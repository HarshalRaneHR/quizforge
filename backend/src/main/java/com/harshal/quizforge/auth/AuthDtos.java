package com.harshal.quizforge.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            // BCrypt ignores bytes after 72, so cap it there.
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    /**
     * Returned by login, register and refresh. {@code token} is the short-lived access token;
     * the refresh token travels only in the HttpOnly cookie. Permissions let the UI show/hide
     * features — the backend still enforces them.
     */
    public record AuthResponse(String token, long expiresInSeconds, String email, String role, List<String> permissions) {}

    public record MeResponse(String email, String role, List<String> permissions) {}

    /** The user's effective permissions (role defaults ± per-user overrides) as authority strings. */
    static List<String> permissionsOf(User user) {
        return user.effectivePermissions().stream().map(Permission::authority).sorted().toList();
    }
}
