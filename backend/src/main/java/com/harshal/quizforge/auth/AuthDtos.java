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

    /** Permissions are sent so the UI can show/hide features; the backend still enforces them. */
    public record AuthResponse(String token, String email, String role, List<String> permissions) {}

    public record MeResponse(String email, String role, List<String> permissions) {}

    static List<String> permissionsOf(Role role) {
        return role.permissions().stream().map(Permission::authority).sorted().toList();
    }
}
