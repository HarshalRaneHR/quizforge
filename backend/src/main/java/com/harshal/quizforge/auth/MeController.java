package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AuthDtos.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current user's profile, role and permissions — the frontend calls this on load to refresh them. */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final AuthService authService;

    @GetMapping("/api/me")
    public MeResponse me(Authentication auth) {
        return authService.me(auth.getName());
    }
}
