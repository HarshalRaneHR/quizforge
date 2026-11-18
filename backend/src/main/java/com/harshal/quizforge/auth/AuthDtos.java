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
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @Size(max = 80) String fullName) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    /** What the login page needs to know before anyone is signed in. */
    public record AuthConfig(boolean publicSignup) {}

    /**
     * Returned by login, register, refresh and password change. {@code token} is the short-lived
     * access token; the refresh token travels only in the HttpOnly cookie. Permissions let the UI
     * show/hide features — the backend still enforces them. {@code mustChangePassword}: send the
     * user to "Set your password" first.
     */
    public record AuthResponse(String token, long expiresInSeconds, String email, String fullName, String role,
                               List<String> permissions, boolean mustChangePassword) {}

    public record MeResponse(String email, String fullName, String role, List<String> permissions, boolean mustChangePassword) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String newPassword) {}

    public record UpdateProfileRequest(@NotBlank @Size(max = 80) String fullName) {}

    /** The user's effective permissions (role defaults ± per-user overrides) as authority strings. */
    static List<String> permissionsOf(User user) {
        return user.effectivePermissions().stream().map(Permission::authority).sorted().toList();
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
