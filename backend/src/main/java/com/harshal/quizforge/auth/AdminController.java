package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.AdminUserService.UserView;
import com.harshal.quizforge.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** User administration. URL rule in SecurityConfig + @PreAuthorize on the service = defence in depth. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin: Users", description = "User management (ADMIN only)")
public class AdminController {

    private final AdminUserService adminUsers;

    public record ChangeRoleRequest(@NotNull Role role) {}

    @Operation(summary = "Search users", description = "Filter by email text and/or role. Requires user:read.")
    @ApiResponse(responseCode = "200", description = "A page of users")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    @GetMapping
    public PageResponse<UserView> listUsers(
            @Parameter(description = "Part of an email, case-insensitive") @RequestParam(required = false) String search,
            @Parameter(description = "Only users with this role") @RequestParam(required = false) Role role,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(adminUsers.search(search, role, pageable));
    }

    @Operation(summary = "Change a user's role", description = "Requires user:manage. Admins can't change their own role.")
    @ApiResponse(responseCode = "200", description = "Role changed")
    @ApiResponse(responseCode = "400", description = "Invalid role, or tried to change own role")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PatchMapping("/{id}/role")
    public UserView changeRole(@Parameter(hidden = true) Authentication auth, @PathVariable Long id,
                               @Valid @RequestBody ChangeRoleRequest req) {
        return adminUsers.changeRole(auth.getName(), id, req.role());
    }
}
