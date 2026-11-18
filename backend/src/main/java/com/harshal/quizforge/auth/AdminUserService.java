package com.harshal.quizforge.auth;

import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.harshal.quizforge.auth.UserRepository.Specs.emailContains;
import static com.harshal.quizforge.auth.UserRepository.Specs.hasRole;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    /** {@code customized}: the user's access differs from their role's defaults. */
    public record UserView(Long id, String email, String role, List<Permission> permissions, boolean customized, Instant createdAt) {
        static UserView of(User u) {
            List<Permission> perms = u.effectivePermissions().stream().sorted().toList();
            return new UserView(u.getId(), u.getEmail(), u.getRole().name(), perms, !u.getPermissionOverrides().isEmpty(), u.getCreatedAt());
        }
    }

    /** Defaults per role, and which permissions an admin may adjust per user. */
    public record RoleInfo(Role role, List<Permission> defaultPermissions) {}

    public record AccessModel(List<RoleInfo> roles, List<Permission> adjustable) {}

    @PreAuthorize("hasAuthority('user:read')")
    public AccessModel accessModel() {
        List<RoleInfo> roles = Arrays.stream(Role.values())
                .map(r -> new RoleInfo(r, r.permissions().stream().sorted().toList())).toList();
        return new AccessModel(roles, Permission.adjustableOnes().stream().sorted().toList());
    }

    @PreAuthorize("hasAuthority('user:read')")
    @Transactional(readOnly = true)
    public Page<UserView> search(String emailFilter, Role roleFilter, Pageable pageable) {
        return users.findAll(emailContains(emailFilter).and(hasRole(roleFilter)), pageable).map(UserView::of);
    }

    /**
     * Admin creates an account directly (e.g. an instructor) with a temporary password.
     * {@code permissions}: the adjustable abilities to give; null = the role's defaults.
     */
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public UserView createUser(String email, String password, Role role, Set<Permission> permissions) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        if (permissions != null) applyAccess(user, permissions);
        return UserView.of(users.save(user));
    }

    /** Changing the role resets access to the new role's defaults (no surprising leftovers). */
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public UserView changeRole(String actingEmail, Long userId, Role newRole) {
        User user = other(actingEmail, userId, "role");
        user.setRole(newRole);   // dirty checking saves it at commit
        user.getPermissionOverrides().clear();
        return UserView.of(user);
    }

    /** Turn adjustable abilities on or off for one user. Takes effect on their next request. */
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public UserView setAccess(String actingEmail, Long userId, Set<Permission> permissions) {
        User user = other(actingEmail, userId, "access");
        applyAccess(user, permissions);
        return UserView.of(user);
    }

    static void applyAccess(User user, Set<Permission> wanted) {
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admins always have full access; change the role to restrict it");
        }
        Set<Permission> notAllowed = EnumSet.noneOf(Permission.class);
        wanted.stream().filter(p -> !p.adjustable()).forEach(notAllowed::add);
        if (!notAllowed.isEmpty()) {
            throw new BadRequestException("These permissions come only with the Admin role: " + notAllowed);
        }
        user.setAccess(wanted);
    }

    /** Stops an admin from accidentally changing their own account and locking themselves out. */
    private User other(String actingEmail, Long userId, String what) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getEmail().equals(actingEmail)) {
            throw new BadRequestException("You can't change your own " + what);
        }
        return user;
    }
}
