package com.harshal.quizforge.auth;

import java.util.Set;
import com.harshal.quizforge.auth.AdminUserService.AccessModel;
import com.harshal.quizforge.auth.AdminUserService.UserView;
import com.harshal.quizforge.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

    /** {@code permissions}: adjustable abilities to give the user; omit to use the role's defaults. */
    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @NotNull Role role,
            Set<Permission> permissions) {}

    public record SetAccessRequest(@NotNull Set<Permission> permissions) {}

    @Operation(summary = "Roles, their default permissions, and which permissions can be adjusted per user")
    @GetMapping("/access-model")
    public AccessModel accessModel() {
        return adminUsers.accessModel();
    }

    @Operation(summary = "Create a user with a role (and optionally custom access)",
            description = "Requires user:manage. permissions = the adjustable abilities to give; omit for the role's defaults.")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "Validation failed, or an admin-only permission was requested")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUserRequest req) {
        return adminUsers.createUser(req.email(), req.password(), req.role(), req.permissions());
    }

    @Operation(summary = "Set a user's adjustable abilities",
            description = "Requires user:manage. Applies on the user's next request. Not for admins or your own account.")
    @ApiResponse(responseCode = "400", description = "Admin-only permission, an admin target, or your own account")
    @PutMapping("/{id}/permissions")
    public UserView setAccess(@Parameter(hidden = true) Authentication auth, @PathVariable Long id,
                              @Valid @RequestBody SetAccessRequest req) {
        return adminUsers.setAccess(auth.getName(), id, req.permissions());
    }

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

    @Operation(summary = "Change a user's role", description = "Requires user:manage. Resets access to the new role's defaults. Admins can't change their own role.")
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
