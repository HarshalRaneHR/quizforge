package com.harshal.quizforge.auth;

import com.harshal.quizforge.auth.RefreshToken.RevokeReason;
import com.harshal.quizforge.common.BadRequestException;
import com.harshal.quizforge.common.ConflictException;
import com.harshal.quizforge.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock RefreshTokenService refreshTokens;
    @InjectMocks AdminUserService service;

    private static User user(long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        return u;
    }

    @Test
    void promotesLearnerToInstructor() {
        User learner = user(2, "learner@test.com", Role.LEARNER);
        when(users.findById(2L)).thenReturn(Optional.of(learner));

        var view = service.changeRole("admin@test.com", 2L, Role.INSTRUCTOR);

        assertThat(learner.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(view.role()).isEqualTo("INSTRUCTOR");
    }

    @Test
    void adminCannotChangeOwnRole() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, "admin@test.com", Role.ADMIN)));

        assertThatThrownBy(() -> service.changeRole("admin@test.com", 1L, Role.LEARNER))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createsPendingUserWithGeneratedOneTimePassword() {
        when(users.existsByEmail("teacher@test.com")).thenReturn(false);
        when(encoder.encode(any())).thenAnswer(inv -> "HASH:" + inv.getArgument(0));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = service.createUser("  Teacher@Test.com ", " Priya Sharma ", Role.INSTRUCTOR, null);

        assertThat(created.temporaryPassword()).matches("[A-Za-z2-9]{4}-[A-Za-z2-9]{4}-[A-Za-z2-9]{4}");
        assertThat(created.user().email()).isEqualTo("teacher@test.com");
        assertThat(created.user().fullName()).isEqualTo("Priya Sharma");
        assertThat(created.user().status()).isEqualTo(AccountState.PENDING);
        // Only the hash is stored, and the person must replace the password at first login
        verify(users).save(argThat(u -> u.getPasswordHash().equals("HASH:" + created.temporaryPassword()) && u.isMustChangePassword()));
    }

    @Test
    void temporaryPasswordsAreUnique() {
        assertThat(TemporaryPasswords.generate()).isNotEqualTo(TemporaryPasswords.generate());
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(users.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser("dup@test.com", "Dup", Role.LEARNER, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownUserIsNotFound() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole("admin@test.com", 99L, Role.LEARNER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resetPasswordMakesAccountPendingAndSignsOutEverywhere() {
        User learner = user(2, "learner@test.com", Role.LEARNER);
        when(users.findById(2L)).thenReturn(Optional.of(learner));
        when(encoder.encode(any())).thenReturn("NEWHASH");

        var reset = service.resetPassword("admin@test.com", 2L);

        assertThat(reset.temporaryPassword()).isNotBlank();
        assertThat(reset.user().status()).isEqualTo(AccountState.PENDING);
        assertThat(learner.getPasswordHash()).isEqualTo("NEWHASH");
        verify(refreshTokens).revokeAll(learner, RevokeReason.PASSWORD_RESET);
    }

    @Test
    void adminCanSetAPasswordDirectlyAndSkipTheForcedChange() {
        User learner = user(2, "learner@test.com", Role.LEARNER);
        when(users.findById(2L)).thenReturn(Optional.of(learner));
        when(encoder.encode("Chosen-pass-1")).thenReturn("CHOSENHASH");

        var reset = service.resetPassword("admin@test.com", 2L, "Chosen-pass-1", false);

        assertThat(reset.temporaryPassword()).isNull();   // the admin already knows it; never echoed back
        assertThat(learner.getPasswordHash()).isEqualTo("CHOSENHASH");
        assertThat(learner.isMustChangePassword()).isFalse();
        assertThat(reset.user().status()).isEqualTo(AccountState.ACTIVE);
        verify(refreshTokens).revokeAll(learner, RevokeReason.PASSWORD_RESET);
    }

    @Test
    void adminChosenPasswordCanStillRequireAChange() {
        User learner = user(2, "learner@test.com", Role.LEARNER);
        when(users.findById(2L)).thenReturn(Optional.of(learner));

        var reset = service.resetPassword("admin@test.com", 2L, "Chosen-pass-1", true);

        assertThat(reset.user().status()).isEqualTo(AccountState.PENDING);
    }

    @Test
    void adminChosenPasswordMustBeLongEnough() {
        when(users.findById(2L)).thenReturn(Optional.of(user(2, "learner@test.com", Role.LEARNER)));

        assertThatThrownBy(() -> service.resetPassword("admin@test.com", 2L, "short", false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminResetsOwnPasswordFromAccountPageInstead() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, "admin@test.com", Role.ADMIN)));

        assertThatThrownBy(() -> service.resetPassword("admin@test.com", 1L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void deactivateBlocksAccountAndEndsSessionsButKeepsIt() {
        User learner = user(2, "learner@test.com", Role.LEARNER);
        when(users.findById(2L)).thenReturn(Optional.of(learner));

        var view = service.deactivate("admin@test.com", 2L);

        assertThat(view.status()).isEqualTo(AccountState.DEACTIVATED);
        assertThat(learner.isActive()).isFalse();
        verify(refreshTokens).revokeAll(learner, RevokeReason.DEACTIVATED);
        verify(users, never()).delete(any(User.class));

        assertThat(service.reactivate("admin@test.com", 2L).status()).isEqualTo(AccountState.ACTIVE);
    }

    @Test
    void cannotDeactivateYourself() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, "admin@test.com", Role.ADMIN)));

        assertThatThrownBy(() -> service.deactivate("admin@test.com", 1L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void theLastActiveAdminCanNeverBeDeactivatedOrDemoted() {
        User other = user(2, "other-admin@test.com", Role.ADMIN);
        when(users.findById(2L)).thenReturn(Optional.of(other));
        when(users.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.deactivate("admin@test.com", 2L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("at least one active admin");
        assertThatThrownBy(() -> service.changeRole("admin@test.com", 2L, Role.LEARNER))
                .isInstanceOf(BadRequestException.class);
        assertThat(other.isActive()).isTrue();
        assertThat(other.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void anotherAdminCanBeDeactivatedWhenOneRemains() {
        User other = user(2, "other-admin@test.com", Role.ADMIN);
        when(users.findById(2L)).thenReturn(Optional.of(other));
        when(users.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE)).thenReturn(2L);

        assertThat(service.deactivate("admin@test.com", 2L).status()).isEqualTo(AccountState.DEACTIVATED);
    }
}
