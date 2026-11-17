package com.harshal.quizforge.auth;

import com.harshal.quizforge.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin")       // ADMIN-only via SecurityConfig
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository users;

    public record UserView(Long id, String email, String role, Instant createdAt) {}

    @GetMapping("/users")
    public PageResponse<UserView> listUsers(Pageable pageable) {
        return PageResponse.of(users.findAll(pageable)
                .map(u -> new UserView(u.getId(), u.getEmail(), u.getRole().name(), u.getCreatedAt())));
    }
}
