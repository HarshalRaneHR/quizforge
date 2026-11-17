package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AdminUserService.UserView;
import com.harshal.quizforge.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** User administration. URL rule in SecurityConfig + @PreAuthorize on the service = defence in depth. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUsers;

    public record ChangeRoleRequest(@NotNull Role role) {}

    @GetMapping
    public PageResponse<UserView> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(adminUsers.search(search, role, pageable));
    }

    @PatchMapping("/{id}/role")
    public UserView changeRole(Authentication auth, @PathVariable Long id, @Valid @RequestBody ChangeRoleRequest req) {
        return adminUsers.changeRole(auth.getName(), id, req.role());
    }
}
