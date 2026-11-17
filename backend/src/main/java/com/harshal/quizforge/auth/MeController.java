package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current user's profile, role and permissions — the frontend calls this on load to refresh them. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Account", description = "The signed-in user")
public class MeController {

    private final AuthService authService;

    @Operation(summary = "Current user's email, role and permissions")
    @GetMapping("/api/me")
    public MeResponse me(@Parameter(hidden = true) Authentication auth) {
        return authService.me(auth.getName());
    }
}
