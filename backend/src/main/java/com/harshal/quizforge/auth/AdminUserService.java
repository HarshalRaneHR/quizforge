package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.RefreshToken.RevokeReason;
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

import static com.harshal.quizforge.auth.UserRepository.Specs.hasRole;
import static com.harshal.quizforge.auth.UserRepository.Specs.inState;
import static com.harshal.quizforge.auth.UserRepository.Specs.textMatches;

/**
 * Accounts are created and looked after by admins (invite-only). People are never deleted:
 * deactivating keeps their attempts, grades and quizzes intact.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;

    /** {@code customized}: the user's access differs from their role's defaults. */
    public record UserView(Long id, String email, String fullName, String role, AccountState status,
                           List<Permission> permissions, boolean customized, Instant createdAt, Instant lastLoginAt) {
        static UserView of(User u) {
            List<Permission> perms = u.effectivePermissions().stream().sorted().toList();
            return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), AccountState.of(u),
                    perms, !u.getPermissionOverrides().isEmpty(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    /** The one-time password is returned once and never stored in plain text. */
    public record CreatedUser(UserView user, String temporaryPassword) {}

    public record PasswordReset(UserView user, String temporaryPassword) {}

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
    public Page<UserView> search(String text, Role roleFilter, AccountState stateFilter, Pageable pageable) {
        return users.findAll(textMatches(text).and(hasRole(roleFilter)).and(inState(stateFilter)), pageable).map(UserView::of);
    }

    /**
     * Admin creates an account with a generated one-time password. The person must choose their own
     * password at first login. {@code permissions}: the adjustable abilities to give; null = the role's defaults.
     */
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public CreatedUser createUser(String email, String fullName, Role role, Set<Permission> permissions) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw new ConflictException("Email already registered");
        }
        String temporary = TemporaryPasswords.generate();
        User user = new User();
        user.setEmail(normalized);
        user.setFullName(AuthDtos.blankToNull(fullName));
        user.setPasswordHash(encoder.encode(temporary));
        user.setMustChangePassword(true);
        user.setRole(role);
        if (permissions != null) applyAccess(user, permissions);
        return new CreatedUser(UserView.of(users.save(user)), temporary);
    }

    /** Changing the role resets access to the new role's defaults (no surprising leftovers). */
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public UserView changeRole(String actingEmail, Long userId, Role newRole) {
        User user = other(actingEmail, userId, "role");
        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN) ensureAnotherActiveAdmin(user);
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

    /** New one-time password for someone who forgot theirs. Signs them out everywhere. */
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public PasswordReset resetPassword(String actingEmail, Long userId) {
        return resetPassword(actingEmail, userId, null, true);
    }

    /**
     * Reset with a password the admin chose ({@code chosenPassword}), or a generated one when null.
     * {@code requireChange}: the person must pick their own at next login. A generated password always
     * requires it. {@code temporaryPassword} in the result is only set when it was generated.
     */
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public PasswordReset resetPassword(String actingEmail, Long userId, String chosenPassword, boolean requireChange) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getEmail().equals(actingEmail)) {
            throw new BadRequestException("Change your own password from your Account page");
        }
        if (!user.isActive()) {
            throw new BadRequestException("Reactivate the account before resetting its password");
        }
        boolean generated = chosenPassword == null;
        if (!generated && (chosenPassword.length() < 8 || chosenPassword.length() > 72)) {
            throw new BadRequestException("The password must be 8-72 characters");
        }
        String password = generated ? TemporaryPasswords.generate() : chosenPassword;
        user.setPasswordHash(encoder.encode(password));
        user.setMustChangePassword(generated || requireChange);
        refreshTokens.revokeAll(user, RevokeReason.PASSWORD_RESET);
        return new PasswordReset(UserView.of(user), generated ? password : null);
    }

    /** Blocks login and ends every session immediately. Nothing is deleted. */
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public UserView deactivate(String actingEmail, Long userId) {
        User user = other(actingEmail, userId, "account status");
        if (!user.isActive()) return UserView.of(user);
        if (user.getRole() == Role.ADMIN) ensureAnotherActiveAdmin(user);
        user.setStatus(AccountStatus.DEACTIVATED);
        refreshTokens.revokeAll(user, RevokeReason.DEACTIVATED);
        return UserView.of(user);
    }

    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public UserView reactivate(String actingEmail, Long userId) {
        User user = other(actingEmail, userId, "account status");
        user.setStatus(AccountStatus.ACTIVE);
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

    /** QuizForge must always keep at least one admin who can log in. */
    private void ensureAnotherActiveAdmin(User admin) {
        long activeAdmins = users.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE);
        if (admin.isActive() && activeAdmins <= 1) {
            throw new BadRequestException("QuizForge needs at least one active admin");
        }
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
